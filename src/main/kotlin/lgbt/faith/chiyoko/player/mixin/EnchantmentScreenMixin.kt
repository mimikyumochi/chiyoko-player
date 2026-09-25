package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.client.ChiyokoPlayerClient
import lgbt.faith.chiyoko.player.ItemEnchantData
import lgbt.faith.chiyoko.player.functions.EnchantFunctions
import lgbt.faith.chiyoko.player.functions.XpSeedCracker
import lgbt.faith.chiyoko.player.pluralKey
import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.LocalCapture
import java.util.*

@Mixin(AbstractContainerScreen::class)
interface AbstractContainerScreenAccessor {
    @Accessor("menu")
    fun chiyoko_getMenu(): AbstractContainerMenu

    @Accessor("imageWidth")
    fun chiyoko_getImageWidth(): Int
}

@Mixin(AbstractContainerMenu::class)
abstract class AbstractContainerMenuMixin {

    @Inject(method = ["setData"], at = [At("TAIL")])
    private fun onSetData(id: Int, data: Int, ci: CallbackInfo) {
        val menu = (this as? AbstractContainerMenu) as? EnchantmentMenu ?: return

        if (id == 3 && data != 0) {
            ChiyokoPlayerClient.partialXpSeed = data
        }

        if (id == 9 && ChiyokoPlayerClient.partialXpSeed != null) {
            val itemStack = menu.getSlot(0).item
            if (itemStack.isEmpty) return

            if (menu.costs.all { it == 0 } || menu.enchantClue.all { it == -1 }) return

            val (enchantability, eligibleEnchantments) = ItemEnchantData.of(itemStack.item)

            val crackedSeed = XpSeedCracker.getOrCrackSeed(
                partialSeed = ChiyokoPlayerClient.partialXpSeed!!,
                menu = menu,
                enchantability = enchantability,
                eligibleEnchantments = eligibleEnchantments,
                isBook = itemStack.item == Items.BOOK
            )

            if (crackedSeed != null) {
                ChiyokoPlayerClient.xpSeed = crackedSeed
            }
        }
    }
}

@Mixin(EnchantmentScreen::class)
abstract class EnchantmentScreenMixin {

    @Inject(
        method = ["extractRenderState"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V", shift = At.Shift.BEFORE)],
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private fun injectFullEnchantData(
        graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTicks: Float, ci: CallbackInfo,
        a: Float, infiniteMaterials: Boolean, gold: Int, i: Int, minLevel: Int, enchant: Optional<*>, enchantLevel: Int, cost: Int, texts: MutableList<Component>
    ) {
        val mc = Minecraft.getInstance()
        val xpSeed = ChiyokoPlayerClient.xpSeed ?: return

        val menu = (this as AbstractContainerScreenAccessor).chiyoko_getMenu() as EnchantmentMenu
        val itemStack = menu.getSlot(0).item
        if (itemStack.isEmpty) return

        val (enchantability, eligibleEnchantments) = ItemEnchantData.of(itemStack.item)

        val rand = LCG()
        rand.setSeed((xpSeed + i).toLong())

        val results = EnchantFunctions.enchantTableSlot(
            rng = rand,
            enchantability = enchantability,
            eligibleIds = eligibleEnchantments,
            baseCost = menu.costs[i],
            isBook = itemStack.item == Items.BOOK
        )

        texts.add(CommonComponents.EMPTY)

        val seedCount = XpSeedCracker.possibleSeeds.size
        when {
            seedCount > 1 -> {
                texts.add(
                    Component.translatable(pluralKey("chiyoko-player.tooltip.possible_seeds", seedCount), seedCount)
                        .withStyle(ChatFormatting.YELLOW)
                )
                texts.add(
                    Component.translatable("chiyoko-player.tooltip.swap_item")
                        .withStyle(ChatFormatting.RED)
                )
                texts.add(
                    Component.translatable("chiyoko-player.tooltip.best_guess")
                        .withStyle(ChatFormatting.DARK_PURPLE)
                )
            }
            seedCount == 0 -> {
                texts.add(
                    Component.translatable("chiyoko-player.tooltip.failed")
                        .withStyle(ChatFormatting.DARK_RED)
                )
                return
            }
            else -> {
                texts.add(
                    Component.translatable("chiyoko-player.tooltip.predicted")
                        .withStyle(ChatFormatting.DARK_PURPLE)
                )
            }
        }

        results.forEach {
            texts.add(
                Component.translatable(
                    "container.enchant.clue",
                    Enchantment.getFullname(it.enchantment, it.level)
                ).withStyle(ChatFormatting.GRAY)
            )
        }
    }
}