package lgbt.faith.chiyoko.player

import lgbt.faith.chiyoko.player.functions.EligibleEnchantments
import lgbt.faith.chiyoko.player.functions.Enchantability
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item

data class ItemEnchantData(
    val enchantability: Int,
    val eligibleEnchantments: Set<String>
) {
    companion object {
        fun of(item: Item): ItemEnchantData {
            val enchantability = Enchantability.getEnchantability(item)
            val eligible = EligibleEnchantments.getEligibleEnchantments(item)
                .intersect(EligibleEnchantments.ENCHANT_TABLE)
                .sortedBy { EligibleEnchantments.legacyOrderIndex(it) }
                .toSet()

            return ItemEnchantData(enchantability, eligible)
        }
    }
}

// lang files have no plurals so counted keys end in .one or .other
fun pluralKey(key: String, count: Int) = if (count == 1) "$key.one" else "$key.other"

fun sendOverlay(key: String, color: ChatFormatting = ChatFormatting.WHITE, vararg args: Any) {
    val mc = Minecraft.getInstance()
    mc.execute { mc.player?.sendOverlayMessage(Component.translatable(key, *args).withStyle(color)) }
}
