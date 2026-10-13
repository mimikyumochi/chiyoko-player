package lgbt.faith.chiyoko.player.commands

import com.mojang.brigadier.CommandDispatcher
import lgbt.faith.chiyoko.player.screens.AutoCrackScreen
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object AutoCrackCommand {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("autocrack")
                .executes {
                    AutoCrackScreen.open()
                    1
                }
        )
    }
}
