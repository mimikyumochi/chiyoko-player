package lgbt.faith.chiyoko.player.screens

import lgbt.faith.chiyoko.player.ChiyokoConfig
import lgbt.faith.chiyoko.player.functions.AutoCracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class AutoCrackScreen : Screen(Component.translatable("chiyoko-player.autocrack.screen.title")) {

    companion object {
        private var openNextTick = false

        // commands run from chat which closes itself right after, so the screen opens on the next tick
        fun open() {
            openNextTick = true
        }

        fun tick(mc: Minecraft) {
            if (!openNextTick) return
            openNextTick = false
            //? if >=26.2 {
            mc.gui.setScreen(AutoCrackScreen())
            //?} else {
            /*mc.setScreen(AutoCrackScreen())
            *///?}
        }
    }

    private val layout = HeaderAndFooterLayout(this)

    override fun init() {
        layout.addTitleHeader(title, font)

        val options = layout.addToContents(LinearLayout.vertical().spacing(8))
        options.addChild(toggle("enabled", ChiyokoConfig.autocrack) {
            ChiyokoConfig.autocrack = it
            if (!it) AutoCracker.cancel()
        })
        options.addChild(toggle("armour", ChiyokoConfig.autocrackArmour) { ChiyokoConfig.autocrackArmour = it })
        options.addChild(toggle("drops", ChiyokoConfig.autocrackDrops) { ChiyokoConfig.autocrackDrops = it })

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE) { onClose() }.width(200).build())

        layout.visitWidgets { addRenderableWidget(it) }
        repositionElements()
    }

    private fun toggle(key: String, value: Boolean, onChange: (Boolean) -> Unit): CycleButton<Boolean> {
        val tooltip = Tooltip.create(Component.translatable("chiyoko-player.autocrack.screen.$key.tooltip"))
        return CycleButton.onOffBuilder(value)
            .withTooltip { tooltip }
            .create(0, 0, 200, 20, Component.translatable("chiyoko-player.autocrack.screen.$key")) { _, new ->
                onChange(new)
            }
    }

    override fun repositionElements() {
        layout.arrangeElements()
    }
}
