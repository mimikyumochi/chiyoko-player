package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.DropCracker
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(AnvilMenu::class)
abstract class AnvilMenuMixin {

    // the client runs onTake too when taking the result so this is when the server rolls the chip
    @Inject(method = ["onTake"], at = [At("HEAD")])
    private fun onTakeResult(player: Player, carried: ItemStack, ci: CallbackInfo) {
        if (!player.level().isClientSide() || player.hasInfiniteMaterials()) return
        DropCracker.onXpAction { nextFloat() }
    }
}

@Mixin(MultiPlayerGameMode::class)
abstract class EnchantButtonMixin {

    // the enchant screen only sends this after its own checks pass
    // the server then runs onEnchantmentPerformed which uses one nextInt for the new xp seed
    @Inject(method = ["handleInventoryButtonClick"], at = [At("HEAD")])
    private fun onButtonClick(containerId: Int, buttonId: Int, ci: CallbackInfo) {
        val menu = Minecraft.getInstance().player?.containerMenu as? EnchantmentMenu ?: return
        if (menu.containerId != containerId || buttonId !in 0..2) return
        DropCracker.onXpAction { nextInt() }
    }
}
