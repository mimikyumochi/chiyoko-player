package lgbt.faith.chiyoko.player.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import lgbt.faith.chiyoko.player.functions.EnchantPredictor
import lgbt.faith.chiyoko.player.functions.EnchantTarget
import lgbt.faith.chiyoko.player.functions.EligibleEnchantments
import lgbt.faith.chiyoko.player.functions.Enchantment
import lgbt.faith.chiyoko.player.pluralKey
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.util.Formatting
import net.minecraft.command.CommandSource
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.item.Item
import java.util.concurrent.CompletableFuture

object PredictEnchant {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {

        dispatcher.register(
            ClientCommandManager.literal("predict")
                .then(
                    ClientCommandManager.argument(
                        "item",
                        StringArgumentType.word()
                    )
                        .suggests { _, builder ->
                            itemSuggestions(builder)
                        }
                        .then(enchantArgument(1))
                )
        )
    }

    // builds enchant and level arguments for up to 3 enchants
    private fun enchantArgument(index: Int): RequiredArgumentBuilder<FabricClientCommandSource, String> {
        val levelArgument = ClientCommandManager.argument(
            "level$index",
            IntegerArgumentType.integer(1)
        )
            .suggests { ctx, builder ->
                levelSuggestions(ctx, builder, "enchant$index")
            }
            .executes { ctx ->
                execute(ctx.source, index, ctx)
            }

        if (index < 3) {
            levelArgument.then(enchantArgument(index + 1))
        }

        return ClientCommandManager.argument(
            "enchant$index",
            StringArgumentType.word()
        )
            .suggests { ctx, builder ->
                enchantSuggestions(ctx, builder)
            }
            .then(levelArgument)
    }

    private fun itemFromName(itemName: String): Item? {
        return Registries.ITEM
            .getEntry(Identifier.ofVanilla(itemName))
            .map { it.value() }
            .orElse(null)
    }


    private fun itemSuggestions(
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {

        val items = Registries.ITEM.ids
            .filter { id ->

                val item = Registries.ITEM
                    .getEntry(id)
                    .map { it.value() }
                    .orElse(null)

                item != null &&
                        EligibleEnchantments.getEligibleEnchantments(item)
                            .intersect(EligibleEnchantments.ENCHANT_TABLE)
                            .isNotEmpty()
            }
            .map { it.path }

        return CommandSource.suggestMatching(
            items,
            builder
        )
    }


    private fun enchantSuggestions(
        ctx: CommandContext<FabricClientCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {

        val itemName = StringArgumentType.getString(ctx, "item")

        val item = itemFromName(itemName) ?: return builder.buildFuture()


        val used = buildSet {
            for (argument in listOf("enchant1", "enchant2")) {
                try {
                    add(StringArgumentType.getString(ctx, argument))
                } catch (_: Exception) {
                }
            }
        }


        val enchants = EligibleEnchantments.getEligibleEnchantments(item)
            .intersect(EligibleEnchantments.ENCHANT_TABLE)
            .filter { it !in used }


        return CommandSource.suggestMatching(
            enchants,
            builder
        )
    }


    private fun levelSuggestions(
        ctx: CommandContext<FabricClientCommandSource>,
        builder: SuggestionsBuilder,
        enchantArgument: String
    ): CompletableFuture<Suggestions> {

        val enchantName = StringArgumentType.getString(ctx, enchantArgument)

        val enchant = Enchantment[enchantName]
            ?: return builder.buildFuture()

        return CommandSource.suggestMatching(
            (1..enchant.maxLevel)
                .map { it.toString() },
            builder
        )
    }


    private fun execute(
        source: FabricClientCommandSource,
        count: Int,
        ctx: CommandContext<FabricClientCommandSource>
    ): Int {
        val itemName = StringArgumentType.getString(ctx, "item")
        val item = itemFromName(itemName)

        if (item == null) {
            source.sendError(
                Text.translatable("chiyoko-player.predict.unknown_item", itemName)
                    .formatted(Formatting.RED)
            )
            return 0
        }

        val targets = (1..count).map { index ->
            EnchantTarget(
                Enchantment[StringArgumentType.getString(ctx, "enchant$index")]!!,
                IntegerArgumentType.getInteger(ctx, "level$index")
            )
        }

        if (targets.size != targets.toSet().size) {
            source.sendError(
                Text.translatable("chiyoko-player.predict.duplicate")
                    .formatted(Formatting.RED)
            )
            return 0
        }

        val start = EnchantPredictor.predictAsync(item, targets) { outcome ->
            when (outcome) {
                is EnchantPredictor.Outcome.Found -> source.sendFeedback(resultMessage(outcome.result))
                EnchantPredictor.Outcome.NotFound -> source.sendFeedback(
                    Text.translatable("chiyoko-player.predict.no_result").formatted(Formatting.RED)
                )
                EnchantPredictor.Outcome.TimedOut -> source.sendError(
                    Text.translatable("chiyoko-player.predict.timed_out").formatted(Formatting.RED)
                )
                EnchantPredictor.Outcome.Failed -> source.sendError(
                    Text.translatable("chiyoko-player.predict.failed").formatted(Formatting.RED)
                )
                EnchantPredictor.Outcome.Stale -> source.sendError(
                    Text.translatable("chiyoko-player.predict.stale").formatted(Formatting.YELLOW)
                )
            }
        }

        when (start) {
            EnchantPredictor.Start.NOT_CRACKED -> {
                source.sendError(Text.translatable("chiyoko-player.predict.not_cracked").formatted(Formatting.RED))
                return 0
            }
            EnchantPredictor.Start.BUSY -> {
                source.sendError(Text.translatable("chiyoko-player.predict.busy").formatted(Formatting.RED))
                return 0
            }
            EnchantPredictor.Start.STARTED -> source.sendFeedback(
                Text.translatable("chiyoko-player.predict.running").formatted(Formatting.GRAY)
            )
        }

        return 1
    }

    private fun resultMessage(result: EnchantPredictor.Result): Text {
        val stacks = result.drops / 64
        val remainder = result.drops % 64
        val stacksText = Text.translatable(pluralKey("chiyoko-player.predict.stack", stacks), stacks)
        val dropsText = when {
            stacks == 0 -> Text.literal("$remainder")
            remainder == 0 -> stacksText
            else -> Text.translatable("chiyoko-player.predict.stacks_and", stacksText, remainder)
        }
        // whole stacks read as drop 2 stacks otherwise the item count after the stacks decides the plural
        val dropKey = if (stacks > 0 && remainder == 0) {
            "chiyoko-player.predict.step.drop.stacks"
        } else {
            pluralKey("chiyoko-player.predict.step.drop", remainder)
        }

        fun step(number: Int, key: String, vararg args: Text) =
            Text.literal("$number. ").formatted(Formatting.DARK_GREEN)
                .append(Text.translatable(key, *args).formatted(Formatting.GREEN))
                .append("\n")

        val message = Text.empty()
            .append(step(1, dropKey, dropsText.formatted(Formatting.AQUA)))
            .append(step(2, pluralKey("chiyoko-player.predict.step.bookshelves", result.bookshelves),
                Text.literal("${result.bookshelves}").formatted(Formatting.AQUA)))
            .append(step(3, "chiyoko-player.predict.step.enchant_once"))
            .append(step(4, "chiyoko-player.predict.step.enchant_slot",
                Text.translatable("chiyoko-player.predict.slot", result.slot + 1).formatted(Formatting.AQUA)))
            .append(Text.translatable("chiyoko-player.predict.warning").formatted(Formatting.YELLOW))

        return message
    }
}
