package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.EquipCracker
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
abstract class EquipObserverMixin {

    @Inject(method = ["handleSoundEvent"], at = [At("TAIL")])
    private fun onSound(packet: ClientboundSoundPacket, ci: CallbackInfo) {
        EquipCracker.onSound(packet)
    }
}