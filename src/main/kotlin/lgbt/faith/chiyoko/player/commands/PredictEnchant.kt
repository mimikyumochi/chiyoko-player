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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.concurrent.CompletableFuture

object PredictEnchant {

    // marks the held item suggestion so CommandSuggestionsMixin can pull it to the top
    val HELD_ITEM_TOOLTIP: Component = Component.translatable("chiyoko-player.predict.held_item")

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {

        dispatcher.register(
            ClientCommands.literal("predict")
                .then(
                    ClientCommands.argument(
                        "item",
                        StringArgumentType.word()
                    )
                        .suggests { _, builder ->
                            itemSuggestions(builder)
                        }
                        .then(
                            // any number of "<enchant> <level>" pairs
                            ClientCommands.argument(
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
        return BuiltInRegistries.ITEM
            .get(Identifier.withDefaultNamespace(itemName))
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

        val items = BuiltInRegistries.ITEM.keySet()
            .filter { id ->

                val item = BuiltInRegistries.ITEM
                    .get(id)
                    .map { it.value() }
                    .orElse(null)

                item != null && tableEnchantments(item).isNotEmpty()
            }
            .map { it.path }

        // main hand first, then off hand, as long as it can go in the table
        val player = Minecraft.getInstance().player
        val held = listOfNotNull(player?.mainHandItem?.item, player?.offhandItem?.item)
            .firstOrNull { tableEnchantments(it).isNotEmpty() }
            ?.let { BuiltInRegistries.ITEM.getKey(it).path }

        val remaining = builder.remainingLowerCase
        for (name in items) {
            if (SharedSuggestionProvider.matchesSubStr(remaining, name)) {
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

            return SharedSuggestionProvider.suggest(
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

        return SharedSuggestionProvider.suggest(
            enchants,
            tokenBuilder
        )
    }


    private fun execute(
        source: FabricClientCommandSource,
        ctx: CommandContext<FabricClientCommandSource>
    ): Int {
        fun error(key: String, vararg args: Any): Int {
            source.sendError(Component.translatable(key, *args).withStyle(ChatFormatting.RED))
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
                    Component.translatable(
                        "chiyoko-player.predict.no_result",
                        EnchantPredictor.MAX_DROPS / 64
                    ).withStyle(ChatFormatting.RED)
                )
                EnchantPredictor.Outcome.TimedOut -> source.sendError(
                    Component.translatable("chiyoko-player.predict.timed_out").withStyle(ChatFormatting.RED)
                )
                EnchantPredictor.Outcome.Failed -> source.sendError(
                    Component.translatable("chiyoko-player.predict.failed").withStyle(ChatFormatting.RED)
                )
                EnchantPredictor.Outcome.Stale -> source.sendError(
                    Component.translatable("chiyoko-player.predict.stale").withStyle(ChatFormatting.YELLOW)
                )
            }
        }

        when (start) {
            EnchantPredictor.Start.NOT_CRACKED -> {
                // predicts by itself once auto crack finishes
                if (AutoCracker.start { execute(source, ctx) }) {
                    source.sendFeedback(
                        Component.translatable("chiyoko-player.predict.autocracking").withStyle(ChatFormatting.GRAY)
                    )
                    return 1
                }
                source.sendError(Component.translatable("chiyoko-player.predict.not_cracked").withStyle(ChatFormatting.RED))
                return 0
            }
            EnchantPredictor.Start.BUSY -> {
                source.sendError(Component.translatable("chiyoko-player.predict.busy").withStyle(ChatFormatting.RED))
                return 0
            }
            EnchantPredictor.Start.STARTED -> source.sendFeedback(
                Component.translatable("chiyoko-player.predict.running").withStyle(ChatFormatting.GRAY)
            )
        }

        return 1
    }

    private fun resultMessage(result: EnchantPredictor.Result): Component {
        val stacks = result.drops / 64
        val remainder = result.drops % 64
        val stacksText = Component.translatable(pluralKey("chiyoko-player.predict.stack", stacks), stacks)
        val dropsText = when {
            stacks == 0 -> Component.literal("$remainder")
            remainder == 0 -> stacksText
            else -> Component.translatable("chiyoko-player.predict.stacks_and", stacksText, remainder)
        }
        // whole stacks read as drop 2 stacks otherwise the item count after the stacks decides the plural
        val dropKey = if (stacks > 0 && remainder == 0) {
            "chiyoko-player.predict.step.drop.stacks"
        } else {
            pluralKey("chiyoko-player.predict.step.drop", remainder)
        }

        fun step(number: Int, key: String, vararg args: Component) =
            Component.literal("$number. ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.translatable(key, *args).withStyle(ChatFormatting.GREEN))
                .append("\n")

        val message = Component.empty()
            .append(step(1, dropKey, dropsText.withStyle(ChatFormatting.AQUA)))
            .append(step(2, pluralKey("chiyoko-player.predict.step.bookshelves", result.bookshelves),
                Component.literal("${result.bookshelves}").withStyle(ChatFormatting.AQUA)))
            .append(step(3, "chiyoko-player.predict.step.enchant_once"))
            .append(step(4, "chiyoko-player.predict.step.enchant_slot",
                Component.translatable("chiyoko-player.predict.slot", result.slot + 1).withStyle(ChatFormatting.AQUA)))
            .append(Component.translatable("chiyoko-player.predict.warning").withStyle(ChatFormatting.YELLOW))

        return message
    }
}
