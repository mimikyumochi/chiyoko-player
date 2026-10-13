package lgbt.faith.chiyoko.player.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import lgbt.faith.chiyoko.player.ItemEnchantData
import lgbt.faith.chiyoko.player.functions.AutoCracker
import lgbt.faith.chiyoko.player.functions.EnchantPredictor
import lgbt.faith.chiyoko.player.functions.EnchantTarget
import lgbt.faith.chiyoko.player.functions.Enchantment
import lgbt.faith.chiyoko.player.pluralKey
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.util.Formatting
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandSource
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.item.Item
import net.minecraft.item.Items
import java.util.concurrent.CompletableFuture

object PredictEnchant {

    // marks the held item suggestion so CommandSuggestionsMixin can pull it to the top
    val HELD_ITEM_TOOLTIP: Text = Text.translatable("chiyoko-player.predict.held_item")

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
                        .then(
                            // any number of "<enchant> <level>" pairs
                            ClientCommandManager.argument(
                                "enchants",
                                StringArgumentType.greedyString()
                            )
                                .suggests { ctx, builder ->
                                    enchantSuggestions(ctx, builder)
                                }
                                .executes { ctx ->
                                    execute(ctx.source, ctx)
                                }
                        )
                )
        )
    }

    private fun itemFromName(itemName: String): Item? {
        return Registries.ITEM
            .getEntry(Identifier.ofVanilla(itemName))
            .map { it.value() }
            .orElse(null)
    }

    // enchants the table can put on this item, empty if it cant go in a table at all
    private fun tableEnchantments(item: Item): Set<String> {
        if (item == Items.ENCHANTED_BOOK) return emptySet()
        val (enchantability, eligible) = ItemEnchantData.of(item)
        return if (enchantability > 0) eligible else emptySet()
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

                item != null && tableEnchantments(item).isNotEmpty()
            }
            .map { it.path }

        // main hand first, then off hand, as long as it can go in the table
        val player = MinecraftClient.getInstance().player
        val held = listOfNotNull(player?.mainHandStack?.item, player?.offHandStack?.item)
            .firstOrNull { tableEnchantments(it).isNotEmpty() }
            ?.let { Registries.ITEM.getId(it).path }

        val remaining = builder.remainingLowerCase
        for (name in items) {
            if (CommandSource.shouldSuggest(remaining, name)) {
                builder.suggest(name, if (name == held) HELD_ITEM_TOOLTIP else null)
            }
        }
        return builder.buildFuture()
    }


    // suggests an enchant or a level depending on where the cursor is in the pair list
    private fun enchantSuggestions(
        ctx: CommandContext<FabricClientCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {

        val item = itemFromName(StringArgumentType.getString(ctx, "item")) ?: return builder.buildFuture()

        val remaining = builder.remaining
        val tokenStart = remaining.lastIndexOf(' ') + 1
        val previous = remaining.substring(0, tokenStart).split(' ').filter { it.isNotEmpty() }
        val tokenBuilder = builder.createOffset(builder.start + tokenStart)

        if (previous.size % 2 == 1) {
            val enchant = Enchantment[previous.last()] ?: return tokenBuilder.buildFuture()

            return CommandSource.suggestMatching(
                (1..enchant.maxLevel)
                    .map { it.toString() },
                tokenBuilder
            )
        }

        val chosen = previous.filterIndexed { i, _ -> i % 2 == 0 }.mapNotNull { Enchantment[it] }

        val enchants = tableEnchantments(item)
            .filter { id ->
                val enchant = Enchantment[id] ?: return@filter false
                chosen.all { Enchantment.areCompatible(it, enchant) }
            }

        return CommandSource.suggestMatching(
            enchants,
            tokenBuilder
        )
    }


    private fun execute(
        source: FabricClientCommandSource,
        ctx: CommandContext<FabricClientCommandSource>
    ): Int {
        fun error(key: String, vararg args: Any): Int {
            source.sendError(Text.translatable(key, *args).formatted(Formatting.RED))
            return 0
        }

        val itemName = StringArgumentType.getString(ctx, "item")
        val item = itemFromName(itemName)
        val allowed = item?.let { tableEnchantments(it) }.orEmpty()

        if (item == null || allowed.isEmpty()) {
            return error("chiyoko-player.predict.unknown_item", itemName)
        }

        val tokens = StringArgumentType.getString(ctx, "enchants").split(' ').filter { it.isNotEmpty() }
        if (tokens.size % 2 != 0) {
            return error("chiyoko-player.predict.missing_level", tokens.last())
        }

        val targets = mutableListOf<EnchantTarget>()
        for ((enchantName, levelText) in tokens.chunked(2)) {
            val enchant = Enchantment[enchantName]?.takeIf { it.id in allowed }
                ?: return error("chiyoko-player.predict.unknown_enchant", enchantName, itemName)

            val level = levelText.toIntOrNull()?.takeIf { it in 1..enchant.maxLevel }
                ?: return error("chiyoko-player.predict.invalid_level", levelText, enchantName, enchant.maxLevel)

            for (other in targets) {
                if (other.enchantment == enchant) {
                    return error("chiyoko-player.predict.duplicate")
                }
                if (!Enchantment.areCompatible(other.enchantment, enchant)) {
                    return error("chiyoko-player.predict.incompatible", other.enchantment.id, enchant.id)
                }
            }

            targets += EnchantTarget(enchant, level)
        }

        val start = EnchantPredictor.predictAsync(item, targets) { outcome ->
            when (outcome) {
                is EnchantPredictor.Outcome.Found -> source.sendFeedback(resultMessage(outcome.result))
                EnchantPredictor.Outcome.NotFound -> source.sendFeedback(
                    Text.translatable(
                        "chiyoko-player.predict.no_result",
                        EnchantPredictor.MAX_DROPS / 64
                    ).formatted(Formatting.RED)
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
                // predicts by itself once auto crack finishes
                if (AutoCracker.start { execute(source, ctx) }) {
                    source.sendFeedback(
                        Text.translatable("chiyoko-player.predict.autocracking").formatted(Formatting.GRAY)
                    )
                    return 1
                }
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
