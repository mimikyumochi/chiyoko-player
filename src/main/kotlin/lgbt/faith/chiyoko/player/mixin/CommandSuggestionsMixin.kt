package lgbt.faith.chiyoko.player.mixin

import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import lgbt.faith.chiyoko.player.commands.PredictEnchant
import net.minecraft.client.gui.screen.ChatInputSuggestor
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ChatInputSuggestor::class)
abstract class CommandSuggestionsMixin {

    // brigadier sorts suggestions alphabetically so the held item has to be moved up after
    @Inject(method = ["sortSuggestions"], at = [At("RETURN")])
    private fun heldItemFirst(suggestions: Suggestions, cir: CallbackInfoReturnable<MutableList<Suggestion>>) {
        val sorted = cir.returnValue
        val held = sorted.indexOfFirst { it.tooltip === PredictEnchant.HELD_ITEM_TOOLTIP }
        if (held > 0) sorted.add(0, sorted.removeAt(held))
    }
}
