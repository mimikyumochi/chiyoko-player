package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.DropCracker
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.entity.ItemEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPlayerEntity::class)
interface LocalPlayerAccessor {
    @Accessor("lastPitchClient")
    fun chiyoko_getXRotLast(): Float

    @Accessor("lastYawClient")
    fun chiyoko_getYRotLast(): Float
}

@Mixin(ClientPlayNetworkHandler::class)
abstract class DropObserverMixin {

    // TAIL only runs on the main thread
    @Inject(method = ["onEntitySpawn"], at = [At("TAIL")])
    private fun onAddEntity(packet: EntitySpawnS2CPacket, ci: CallbackInfo) {
        if (MinecraftClient.getInstance().world?.getEntityById(packet.entityId) !is ItemEntity) return
        DropCracker.onItemSpawned(packet.x, packet.y, packet.z, packet.uuid, packet.velocity)
    }
}
