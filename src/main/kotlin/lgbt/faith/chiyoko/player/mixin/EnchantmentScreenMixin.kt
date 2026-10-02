package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.client.ChiyokoPlayerClient
import lgbt.faith.chiyoko.player.ItemEnchantData
import lgbt.faith.chiyoko.player.functions.EnchantFunctions
import lgbt.faith.chiyoko.player.functions.XpSeedCracker
import lgbt.faith.chiyoko.player.pluralKey
import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.util.Formatting
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.EnchantmentScreenHandler
import net.minecraft.item.Items
import net.minecraft.enchantment.Enchantment
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.LocalCapture
import java.util.*

@Mixin(HandledScreen::class)
interface AbstractContainerScreenAccessor {
    @Accessor("handler")
    fun chiyoko_getMenu(): ScreenHandler

    @Accessor("backgroundWidth")
    fun chiyoko_getImageWidth(): Int
}

@Mixin(ScreenHandler::class)
abstract class AbstractContainerMenuMixin {

    @Inject(method = ["setProperty"], at = [At("TAIL")])
    private fun onSetData(id: Int, data: Int, ci: CallbackInfo) {
        val menu = (this as? ScreenHandler) as? EnchantmentScreenHandler ?: return

        if (id == 3 && data != 0) {
            ChiyokoPlayerClient.partialXpSeed = data
        }

        if (id == 9 && ChiyokoPlayerClient.partialXpSeed != null) {
            val itemStack = menu.getSlot(0).stack
            if (itemStack.isEmpty) return

            if (menu.enchantmentPower.all { it == 0 } || menu.enchantmentId.all { it == -1 }) return

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
        method = ["render"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;II)V", shift = At.Shift.BEFORE)],
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private fun injectFullEnchantData(
        graphics: DrawContext, mouseX: Int, mouseY: Int, partialTicks: Float, ci: CallbackInfo,
        a: Float, infiniteMaterials: Boolean, gold: Int, i: Int, minLevel: Int, enchant: Optional<*>, enchantLevel: Int, cost: Int, texts: MutableList<Text>
    ) {
        val mc = MinecraftClient.getInstance()
        val xpSeed = ChiyokoPlayerClient.xpSeed ?: return

        val menu = (this as AbstractContainerScreenAccessor).chiyoko_getMenu() as EnchantmentScreenHandler
        val itemStack = menu.getSlot(0).stack
        if (itemStack.isEmpty) return

        val (enchantability, eligibleEnchantments) = ItemEnchantData.of(itemStack.item)

        val rand = LCG()
        rand.setSeed((xpSeed + i).toLong())

        val results = EnchantFunctions.enchantTableSlot(
            rng = rand,
            enchantability = enchantability,
            eligibleIds = eligibleEnchantments,
            baseCost = menu.enchantmentPower[i],
            isBook = itemStack.item == Items.BOOK
        )

        texts.add(ScreenTexts.EMPTY)

        val seedCount = XpSeedCracker.possibleSeeds.size
        when {
            seedCount > 1 -> {
                texts.add(
                    Text.translatable(pluralKey("chiyoko-player.tooltip.possible_seeds", seedCount), seedCount)
                        .formatted(Formatting.YELLOW)
                )
                texts.add(
                    Text.translatable("chiyoko-player.tooltip.swap_item")
                        .formatted(Formatting.RED)
                )
                texts.add(
                    Text.translatable("chiyoko-player.tooltip.best_guess")
                        .formatted(Formatting.DARK_PURPLE)
                )
            }
            seedCount == 0 -> {
                texts.add(
                    Text.translatable("chiyoko-player.tooltip.failed")
                        .formatted(Formatting.DARK_RED)
                )
                return
            }
            else -> {
                texts.add(
                    Text.translatable("chiyoko-player.tooltip.predicted")
                        .formatted(Formatting.DARK_PURPLE)
                )
            }
        }

        results.forEach {
            texts.add(
                Text.translatable(
                    "container.enchant.clue",
                    Enchantment.getName(it.enchantment, it.level)
                ).formatted(Formatting.GRAY)
            )
        }
    }
}