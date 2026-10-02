package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.AnvilPredictor
import lgbt.faith.chiyoko.player.pluralKey
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.text.Text
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AnvilScreen::class)
abstract class AnvilScreenMixin {

    // labels are drawn relative to the gui so a negative y is above it
    @Inject(method = ["drawForeground"], at = [At("TAIL")])
    private fun drawChipPrediction(graphics: DrawContext, xm: Int, ym: Int, ci: CallbackInfo) {
        val (line, color) = when (val prediction = AnvilPredictor.predict()) {
            is AnvilPredictor.Prediction.Safe ->
                Text.translatable(pluralKey("chiyoko-player.anvil.safe", prediction.uses), prediction.uses) to 0xFF80FF20.toInt()
            is AnvilPredictor.Prediction.Chips ->
                Text.translatable(pluralKey("chiyoko-player.anvil.chip", prediction.drops), prediction.drops) to 0xFFFF6060.toInt()
            null -> return
        }

        val font = MinecraftClient.getInstance().textRenderer
        val imageWidth = (this as AbstractContainerScreenAccessor).chiyoko_getImageWidth()
        graphics.drawTextWithShadow(font, line, (imageWidth - font.getWidth(line)) / 2, -12, color)
    }
}
