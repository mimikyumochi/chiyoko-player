package lgbt.faith.chiyoko.player.functions

import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import kotlin.math.abs

object EquipCracker {
    private const val POSITION_EPSILON = 0.125 + 1.0E-4
    private const val MATCH_WINDOW_TICKS = 5

    private val LOGGER = LoggerFactory.getLogger("chiyoko-player")
    private val ARMOR_SLOTS = EquipmentSlot.entries.filter { it.type == EquipmentSlot.Type.HUMANOID_ARMOR }

    private data class PendingSound(val sound: Identifier, val seed: Long, val tick: Int)

    private var ticks = 0
    private var entityId: Int? = null
    private var lastArmor: List<ItemStack> = emptyList()
    // equip sounds our own armor changes should produce, mapped to the tick they changed
    private val expectedSounds = mutableMapOf<Identifier, Int>()
    // equip sounds at our position that came in before the slot update
    private val pendingSounds = mutableListOf<PendingSound>()

    fun tick(mc: Minecraft) {
        ticks++
        val player = mc.player ?: return

        val armor = ARMOR_SLOTS.map { player.getItemBySlot(it).copy() }
        // a new player entity means respawn or dimension change so its armor isnt a change
        if (entityId == player.id && lastArmor.size == armor.size) {
            armor.forEachIndexed { i, stack ->
                if (!ItemStack.isSameItemSameComponents(lastArmor[i], stack)) onArmorChanged(stack)
            }
        }
        entityId = player.id
        lastArmor = armor

        expectedSounds.values.removeIf { ticks - it > MATCH_WINDOW_TICKS }
        pendingSounds.removeIf { ticks - it.tick > MATCH_WINDOW_TICKS }
    }

    fun clear() {
        entityId = null
        lastArmor = emptyList()
        expectedSounds.clear()
        pendingSounds.clear()
    }

    // for equips the client wont see as a change, like taking armour off and on in one tick
    fun expectEquip(stack: ItemStack) = onArmorChanged(stack)

    fun isEquipSound(sound: Identifier) = sound.path.startsWith("item.armor.equip")

    // LivingEntity onEquipItem only plays the sound when the item goes into its own equippable slot
    private fun onArmorChanged(stack: ItemStack) {
        val equippable = stack.get(DataComponents.EQUIPPABLE) ?: return
        val sound = equippable.equipSound().unwrapKey().orElse(null)?.identifier() ?: return

        val pending = pendingSounds.firstOrNull { it.sound == sound }
        if (pending != null) {
            pendingSounds.remove(pending)
            crack(pending.seed)
            return
        }
        expectedSounds[sound] = ticks
    }

    // the server plays our equip sound with player random nextLong as the seed
    fun onSound(packet: ClientboundSoundPacket) {
        val player = Minecraft.getInstance().player ?: return
        val sound = packet.sound.unwrapKey().orElse(null)?.identifier() ?: return
        if (!isEquipSound(sound)) return
        if (abs(packet.x - player.x) > POSITION_EPSILON ||
            abs(packet.y - player.y) > POSITION_EPSILON ||
            abs(packet.z - player.z) > POSITION_EPSILON) return

        if (expectedSounds.remove(sound) != null) {
            crack(packet.seed)
        } else {
            pendingSounds.add(PendingSound(sound, packet.seed, ticks))
        }
    }

    private fun crack(seed: Long) {
        val state = stateFromNextLong(seed)
        if (state == null) {
            LOGGER.info("equip seed {} not from a java random", seed)
            return
        }
        LOGGER.info("cracked player rng from equip seed {}", seed)
        RngTracker.onStateCracked(state) { nextLong() }
    }

    // returns the lcg state right after the nextLong that made this value
    fun stateFromNextLong(value: Long): LCG? {
        val second = value.toInt()
        // nextLong adds the second int as signed so a negative one borrows from the first
        val first = (value ushr 32).toInt() + if (second < 0) 1 else 0
        for (low in 0..0xFFFF) {
            val rand = LCG(((first.toLong() and 0xFFFFFFFFL) shl 16) or low.toLong())
            if (rand.next(32) == second) return rand
        }
        return null
    }
}
