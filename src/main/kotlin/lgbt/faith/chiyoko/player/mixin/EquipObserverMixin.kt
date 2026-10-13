package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.EquipCracker
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPlayNetworkHandler::class)
abstract class EquipObserverMixin {

    @Inject(method = ["onPlaySound"], at = [At("TAIL")])
    private fun onSound(packet: PlaySoundS2CPacket, ci: CallbackInfo) {
        EquipCracker.onSound(packet)
    }
}