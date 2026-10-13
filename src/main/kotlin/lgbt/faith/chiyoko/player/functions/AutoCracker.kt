package lgbt.faith.chiyoko.player.functions

import lgbt.faith.chiyoko.player.ChiyokoConfig
import lgbt.faith.chiyoko.player.sendOverlay
import net.minecraft.util.Formatting
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.component.EnchantmentEffectComponentTypes
import net.minecraft.enchantment.EnchantmentHelper
import org.slf4j.LoggerFactory

// cracks the player rng without the player doing anything, by re-equipping armour or throwing items
object AutoCracker {
    private const val EQUIP_TIMEOUT_TICKS = 20
    private const val MAX_DROPS = 10
    private const val DROP_INTERVAL_TICKS = 5
    // a menu we just asked the server to open, throwing before it opens would use the wrong container id
    private const val MENU_WAIT_TICKS = 20
    private const val SOLVE_WAIT_TICKS = 60
    private const val GIVE_UP_TICKS = 20 * 20
    private const val STRAIGHT_DOWN = 90f

    private val LOGGER = LoggerFactory.getLogger("chiyoko-player")
    // the order the inventory menu lays out its armour slots
    private val ARMOR_MENU_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
    private val JUNK = setOf(
        Items.DIRT, Items.COARSE_DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DEEPSLATE,
        Items.NETHERRACK, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.GRAVEL,
        Items.SAND, Items.RED_SAND, Items.BLACKSTONE, Items.BASALT, Items.END_STONE, Items.ROTTEN_FLESH,
    )

    private enum class Phase { IDLE, EQUIPPING, DROPPING }

    private var phase = Phase.IDLE
    private var ticks = 0
    private var dropsLeft = 0
    private var lastDropTick = 0
    private var waitForMenu = false
    // pitch before we looked down, null when we havent turned the camera
    private var restorePitch: Float? = null
    private val onCracked = mutableListOf<() -> Unit>()

    // returns false when it wont crack, then runs onCracked once the rng is known
    fun start(waitForMenu: Boolean = false, then: (() -> Unit)? = null): Boolean {
        if (!ChiyokoConfig.autocrack || RngTracker.sharedRng || RngTracker.state != null) return false
        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return false

        if (phase != Phase.IDLE) {
            then?.let { onCracked += it }
            return true
        }

        this.waitForMenu = waitForMenu
        if (ChiyokoConfig.autocrackArmour && equip(mc, player)) {
            phase = Phase.EQUIPPING
            ticks = 0
            sendOverlay("chiyoko-player.autocrack.equipping", Formatting.AQUA)
        } else if (!startDropping(player)) {
            sendOverlay(nothingToUseKey(), Formatting.RED)
            return false
        }

        then?.let { onCracked += it }
        return true
    }

    fun tick(mc: MinecraftClient) {
        if (phase == Phase.IDLE) return
        val player = mc.player ?: return cancel()
        ticks++

        if (RngTracker.state != null) return onCracked()

        when (phase) {
            Phase.EQUIPPING -> if (ticks > EQUIP_TIMEOUT_TICKS) {
                // the server never played the sound so fall back to drops
                LOGGER.info("auto crack equip sound never came, dropping instead")
                when {
                    !ChiyokoConfig.autocrackDrops -> fail("chiyoko-player.autocrack.failed.no_sound")
                    !startDropping(player) -> fail("chiyoko-player.autocrack.failed.no_sound_no_junk")
                }
            }
            Phase.DROPPING -> {
                if (ticks > GIVE_UP_TICKS) return fail("chiyoko-player.autocrack.failed.timed_out")
                if (waitForMenu && player.currentScreenHandler === player.playerScreenHandler && ticks < MENU_WAIT_TICKS) return
                if (dropsLeft == 0) {
                    if (ticks - lastDropTick > SOLVE_WAIT_TICKS) fail("chiyoko-player.autocrack.failed.out_of_drops", MAX_DROPS)
                    return
                }
                if (!DropCracker.rotationSent(player) || ticks - lastDropTick < DROP_INTERVAL_TICKS) return
                if (!player.currentScreenHandler.cursorStack.isEmpty) return

                val slot = dropSlot(player) ?: return fail("chiyoko-player.autocrack.failed.out_of_junk")
                click(mc, player, slot.id, SlotActionType.THROW)
                DropCracker.onDropSent()
                dropsLeft--
                lastDropTick = ticks
            }
            Phase.IDLE -> {}
        }
    }

    fun onCracked() {
        if (phase == Phase.IDLE) return
        phase = Phase.IDLE
        lookBack()
        val callbacks = onCracked.toList()
        onCracked.clear()
        callbacks.forEach { it() }
    }

    fun cancel() {
        phase = Phase.IDLE
        onCracked.clear()
        lookBack()
    }

    private fun fail(key: String, vararg args: Any) {
        cancel()
        sendOverlay(key, Formatting.RED, *args)
    }

    private fun nothingToUseKey() = when {
        ChiyokoConfig.autocrackArmour && ChiyokoConfig.autocrackDrops -> "chiyoko-player.autocrack.nothing.both"
        ChiyokoConfig.autocrackArmour -> "chiyoko-player.autocrack.nothing.armour"
        ChiyokoConfig.autocrackDrops -> "chiyoko-player.autocrack.nothing.drops"
        else -> "chiyoko-player.autocrack.nothing.disabled"
    }

    private fun startDropping(player: ClientPlayerEntity): Boolean {
        if (!ChiyokoConfig.autocrackDrops || dropSlot(player) == null) return false

        if (player.pitch != STRAIGHT_DOWN) {
            restorePitch = player.pitch
            player.pitch = STRAIGHT_DOWN
            player.lastPitch = STRAIGHT_DOWN
        }

        phase = Phase.DROPPING
        ticks = 0
        lastDropTick = -DROP_INTERVAL_TICKS
        dropsLeft = MAX_DROPS
        sendOverlay("chiyoko-player.autocrack.dropping", Formatting.AQUA)
        return true
    }

    // only if the player hasnt turned the camera themselves since
    private fun lookBack() {
        val pitch = restorePitch ?: return
        restorePitch = null
        val player = MinecraftClient.getInstance().player ?: return
        if (player.pitch != STRAIGHT_DOWN) return
        player.pitch = pitch
        player.lastPitch = pitch
    }

    // the biggest stack of junk in the player's own inventory
    private fun dropSlot(player: ClientPlayerEntity): Slot? =
        player.currentScreenHandler.slots
            .filter {
                it.inventory === player.inventory && it.index < PlayerInventory.MAIN_SIZE &&
                        it.hasStack() && it.canTakeItems(player) && it.stack.item in JUNK
            }
            .maxByOrNull { it.stack.count }

    // the server plays an equip sound seeded from player rng whenever armour goes into its slot
    private fun equip(mc: MinecraftClient, player: ClientPlayerEntity): Boolean {
        val menu = player.playerScreenHandler
        if (player.currentScreenHandler !== menu || !menu.cursorStack.isEmpty) return false

        // curse of binding stops armour coming off outside creative
        fun canMove(stack: ItemStack) =
            player.isCreative || !EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)

        // a worn piece only has to be taken off and put back on
        for ((i, equipSlot) in ARMOR_MENU_SLOTS.withIndex()) {
            val stack = player.getEquippedStack(equipSlot)
            if (!playsEquipSound(stack, equipSlot) || !canMove(stack)) continue

            val armorSlot = PlayerScreenHandler.EQUIPMENT_START + i
            EquipCracker.expectEquip(stack)
            click(mc, player, armorSlot, SlotActionType.PICKUP)
            click(mc, player, armorSlot, SlotActionType.PICKUP)
            return true
        }

        // otherwise borrow a piece into its empty slot and put it back where it was
        for (from in PlayerScreenHandler.INVENTORY_START until PlayerScreenHandler.HOTBAR_END) {
            val stack = menu.getSlot(from).stack
            // stacked equippables like pumpkins would only put one in the slot
            if (stack.count != 1) continue
            val equipSlot = stack.get(DataComponentTypes.EQUIPPABLE)?.slot() ?: continue
            val i = ARMOR_MENU_SLOTS.indexOf(equipSlot)
            if (i < 0 || !player.getEquippedStack(equipSlot).isEmpty) continue
            if (!playsEquipSound(stack, equipSlot) || !canMove(stack)) continue

            val armorSlot = PlayerScreenHandler.EQUIPMENT_START + i
            EquipCracker.expectEquip(stack)
            click(mc, player, from, SlotActionType.PICKUP)
            click(mc, player, armorSlot, SlotActionType.PICKUP)
            click(mc, player, armorSlot, SlotActionType.PICKUP)
            click(mc, player, from, SlotActionType.PICKUP)
            return true
        }
        return false
    }

    private fun playsEquipSound(stack: ItemStack, slot: EquipmentSlot): Boolean {
        val equippable = stack.get(DataComponentTypes.EQUIPPABLE) ?: return false
        val sound = equippable.equipSound().key.orElse(null)?.value ?: return false
        return equippable.slot() == slot && EquipCracker.isEquipSound(sound)
    }

    private fun click(mc: MinecraftClient, player: ClientPlayerEntity, slot: Int, input: SlotActionType) {
        mc.interactionManager?.clickSlot(player.currentScreenHandler.syncId, slot, 0, input, player)
    }
}
