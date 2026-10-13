package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.AutoCracker
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.EnchantingTableBlock
import net.minecraft.world.phys.BlockHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(MultiPlayerGameMode::class)
abstract class AutoCrackMixin {

    // runs before the use packet is sent so the server equips first and opens the menu after
    @Inject(method = ["useItemOn"], at = [At("HEAD")])
    private fun beforeUseBlock(player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult, cir: CallbackInfoReturnable<InteractionResult>) {
        // sneaking with an item places it instead of opening the menu
        if (player.isSecondaryUseActive && (!player.mainHandItem.isEmpty || !player.offhandItem.isEmpty)) return

        val block = player.level().getBlockState(hit.blockPos).block
        if (block is AnvilBlock || block is EnchantingTableBlock) {
            AutoCracker.start(waitForMenu = true)
        }
    }
}
