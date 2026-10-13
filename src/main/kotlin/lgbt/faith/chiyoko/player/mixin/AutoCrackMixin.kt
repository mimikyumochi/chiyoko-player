package lgbt.faith.chiyoko.player.mixin

import lgbt.faith.chiyoko.player.functions.AutoCracker
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.Hand
import net.minecraft.util.ActionResult
import net.minecraft.block.AnvilBlock
import net.minecraft.block.EnchantingTableBlock
import net.minecraft.util.hit.BlockHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ClientPlayerInteractionManager::class)
abstract class AutoCrackMixin {

    // runs before the use packet is sent so the server equips first and opens the menu after
    @Inject(method = ["interactBlock"], at = [At("HEAD")])
    private fun beforeUseBlock(player: ClientPlayerEntity, hand: Hand, hit: BlockHitResult, cir: CallbackInfoReturnable<ActionResult>) {
        // sneaking with an item places it instead of opening the menu
        if (player.shouldCancelInteraction() && (!player.mainHandStack.isEmpty || !player.offHandStack.isEmpty)) return

        val block = player.entityWorld.getBlockState(hit.blockPos).block
        if (block is AnvilBlock || block is EnchantingTableBlock) {
            AutoCracker.start(waitForMenu = true)
        }
    }
}
