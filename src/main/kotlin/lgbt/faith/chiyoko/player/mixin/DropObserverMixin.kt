package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.DropCracker
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.item.ItemEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(LocalPlayer::class)
interface LocalPlayerAccessor {
    @Accessor("xRotLast")
    fun chiyoko_getXRotLast(): Float

    @Accessor("yRotLast")
    fun chiyoko_getYRotLast(): Float
}

@Mixin(ClientPacketListener::class)
abstract class DropObserverMixin {

    // TAIL only runs on the main thread
    @Inject(method = ["handleAddEntity"], at = [At("TAIL")])
    private fun onAddEntity(packet: ClientboundAddEntityPacket, ci: CallbackInfo) {
        if (Minecraft.getInstance().level?.getEntity(packet.id) !is ItemEntity) return
        DropCracker.onItemSpawned(packet.x, packet.y, packet.z, packet.uuid, packet.movement)
    }
}
