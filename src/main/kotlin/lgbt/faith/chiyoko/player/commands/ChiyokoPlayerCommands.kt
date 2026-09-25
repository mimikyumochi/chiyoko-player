package lgbt.faith.chiyoko.player.commands

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object ChiyokoPlayerCommands {

    fun register(
        dispatcher: CommandDispatcher<FabricClientCommandSource>
    ) {
        PredictEnchant.register(dispatcher)
    }
}
