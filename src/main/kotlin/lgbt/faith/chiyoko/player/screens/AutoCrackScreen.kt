package lgbt.faith.chiyoko.player.screens

import lgbt.faith.chiyoko.player.ChiyokoConfig
import lgbt.faith.chiyoko.player.functions.AutoCracker
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget
import net.minecraft.client.gui.widget.DirectionalLayoutWidget
import net.minecraft.client.gui.screen.Screen
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text

class AutoCrackScreen : Screen(Text.translatable("chiyoko-player.autocrack.screen.title")) {

    companion object {
        private var openNextTick = false

        // commands run from chat which closes itself right after, so the screen opens on the next tick
        fun open() {
            openNextTick = true
        }

        fun tick(mc: MinecraftClient) {
            if (!openNextTick) return
            openNextTick = false
            mc.setScreen(AutoCrackScreen())
        }
    }

    private val layout = ThreePartsLayoutWidget(this)

    override fun init() {
        layout.addHeader(title, textRenderer)

        val options = layout.addBody(DirectionalLayoutWidget.vertical().spacing(8))
        options.add(toggle("enabled", ChiyokoConfig.autocrack) {
            ChiyokoConfig.autocrack = it
            if (!it) AutoCracker.cancel()
        })
        options.add(toggle("armour", ChiyokoConfig.autocrackArmour) { ChiyokoConfig.autocrackArmour = it })
        options.add(toggle("drops", ChiyokoConfig.autocrackDrops) { ChiyokoConfig.autocrackDrops = it })

        layout.addFooter(ButtonWidget.builder(ScreenTexts.DONE) { close() }.width(200).build())

        layout.forEachChild { addDrawableChild(it) }
        refreshWidgetPositions()
    }

    private fun toggle(key: String, value: Boolean, onChange: (Boolean) -> Unit): CyclingButtonWidget<Boolean> {
        val tooltip = Tooltip.of(Text.translatable("chiyoko-player.autocrack.screen.$key.tooltip"))
        return CyclingButtonWidget.onOffBuilder(value)
            .tooltip { tooltip }
            .build(0, 0, 200, 20, Text.translatable("chiyoko-player.autocrack.screen.$key")) { _, new ->
                onChange(new)
            }
    }

    override fun refreshWidgetPositions() {
        layout.refreshPositions()
    }
}
