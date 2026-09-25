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
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import java.util.concurrent.CompletableFuture

object PredictEnchant {

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
                        .then(enchantArgument(1))
                )
        )
    }

    // builds enchant and level arguments for up to 3 enchants
    private fun enchantArgument(index: Int): RequiredArgumentBuilder<FabricClientCommandSource, String> {
        val levelArgument = ClientCommands.argument(
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

        return ClientCommands.argument(
            "enchant$index",
            StringArgumentType.word()
        )
            .suggests { ctx, builder ->
                enchantSuggestions(ctx, builder)
            }
            .then(levelArgument)
    }

    private fun itemFromName(itemName: String): Item? {
        return BuiltInRegistries.ITEM
            .get(Identifier.withDefaultNamespace(itemName))
            .map { it.value() }
            .orElse(null)
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

                item != null &&
                        EligibleEnchantments.getEligibleEnchantments(item)
                            .intersect(EligibleEnchantments.ENCHANT_TABLE)
                            .isNotEmpty()
            }
            .map { it.path }

        return SharedSuggestionProvider.suggest(
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


        return SharedSuggestionProvider.suggest(
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

        return SharedSuggestionProvider.suggest(
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
                Component.translatable("chiyoko-player.predict.unknown_item", itemName)
                    .withStyle(ChatFormatting.RED)
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
                Component.translatable("chiyoko-player.predict.duplicate")
                    .withStyle(ChatFormatting.RED)
            )
            return 0
        }

        val start = EnchantPredictor.predictAsync(item, targets) { outcome ->
            when (outcome) {
                is EnchantPredictor.Outcome.Found -> source.sendFeedback(resultMessage(outcome.result))
                EnchantPredictor.Outcome.NotFound -> source.sendFeedback(
                    Component.translatable("chiyoko-player.predict.no_result").withStyle(ChatFormatting.RED)
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
