package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.AnvilPredictor
import lgbt.faith.chiyoko.player.pluralKey
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AnvilScreen::class)
abstract class AnvilScreenMixin {

    // labels are drawn relative to the gui so a negative y is above it
    @Inject(method = ["extractLabels"], at = [At("TAIL")])
    private fun drawChipPrediction(graphics: GuiGraphicsExtractor, xm: Int, ym: Int, ci: CallbackInfo) {
        val (line, color) = when (val prediction = AnvilPredictor.predict()) {
            is AnvilPredictor.Prediction.Safe ->
                Component.translatable(pluralKey("chiyoko-player.anvil.safe", prediction.uses), prediction.uses) to 0xFF80FF20.toInt()
            is AnvilPredictor.Prediction.Chips ->
                Component.translatable(pluralKey("chiyoko-player.anvil.chip", prediction.drops), prediction.drops) to 0xFFFF6060.toInt()
            null -> return
        }

        val font = Minecraft.getInstance().font
        val imageWidth = (this as AbstractContainerScreenAccessor).chiyoko_getImageWidth()
        graphics.text(font, line, (imageWidth - font.width(line)) / 2, -12, color)
    }
}
