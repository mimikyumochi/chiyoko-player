package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.RngTracker
import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.screen.AnvilScreenHandler
import net.minecraft.screen.EnchantmentScreenHandler
import net.minecraft.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AnvilScreenHandler::class)
abstract class AnvilMenuMixin {

    // the client runs onTake too when taking the result so this is when the server rolls the chip
    @Inject(method = ["onTakeOutput"], at = [At("HEAD")])
    private fun onTakeResult(player: PlayerEntity, carried: ItemStack, ci: CallbackInfo) {
        if (!player.entityWorld.isClient() || player.isInCreativeMode()) return
        RngTracker.onXpAction { nextFloat() }
    }
}

@Mixin(ClientPlayerInteractionManager::class)
abstract class EnchantButtonMixin {

    // the enchant screen only sends this after its own checks pass
    // the server then runs onEnchantmentPerformed which uses one nextInt for the new xp seed
    @Inject(method = ["clickButton"], at = [At("HEAD")])
    private fun onButtonClick(containerId: Int, buttonId: Int, ci: CallbackInfo) {
        val menu = MinecraftClient.getInstance().player?.currentScreenHandler as? EnchantmentScreenHandler ?: return
        if (menu.syncId != containerId || buttonId !in 0..2) return
        RngTracker.onXpAction { nextInt() }
    }
}
