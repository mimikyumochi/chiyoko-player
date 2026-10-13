package lgbt.faith.chiyoko.player.client

import lgbt.faith.chiyoko.player.ChiyokoConfig
import lgbt.faith.chiyoko.player.commands.ChiyokoPlayerCommands
import lgbt.faith.chiyoko.player.functions.AutoCracker
import lgbt.faith.chiyoko.player.functions.DropCracker
import lgbt.faith.chiyoko.player.functions.EquipCracker
import lgbt.faith.chiyoko.player.functions.RngTracker
import lgbt.faith.chiyoko.player.screens.AutoCrackScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

class ChiyokoPlayerClient : ClientModInitializer {
    companion object {
        var xpSeed: Int? = null
        var partialXpSeed: Int? = null
    }

    override fun onInitializeClient() {
        ChiyokoConfig.load()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            ChiyokoPlayerCommands.register(dispatcher)
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            xpSeed = null
            partialXpSeed = null

            RngTracker.clear()
            DropCracker.clear()
            EquipCracker.clear()
            AutoCracker.cancel()
        }

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            RngTracker.tick(mc)
            DropCracker.tick(mc)
            EquipCracker.tick(mc)
            AutoCracker.tick(mc)
            AutoCrackScreen.tick(mc)
        }
    }
}
