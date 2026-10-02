package lgbt.faith.chiyoko.player.functions

import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.RegistryKeys
import net.minecraft.screen.EnchantmentScreenHandler

object XpSeedCracker {
    var lastLower16: Int = -1
    var possibleSeeds: List<Int> = emptyList()
    var cachedCrackedSeed: Int = 0

    fun getOrCrackSeed(
        partialSeed: Int,
        menu: EnchantmentScreenHandler,
        enchantability: Int,
        eligibleEnchantments: Set<String>,
        isBook: Boolean
    ): Int? {
        val lower16 = partialSeed

        if (lower16 != lastLower16) {
            lastLower16 = lower16
            possibleSeeds = emptyList()
        }

        if (possibleSeeds.size == 1) {
            return possibleSeeds.first()
        }

        val base16 = lower16 and 0xFFFF
        val validSlots = (0..2).filter { menu.enchantmentPower[it] > 0 && menu.enchantmentId[it] != -1 }

        if (validSlots.isEmpty()) {
            return null
        }

        val mc = MinecraftClient.getInstance()
        val level = mc.world ?: return null
        val registry = level.registryManager.getOrThrow(RegistryKeys.ENCHANTMENT).indexedEntries

        val candidatesToTest = possibleSeeds.ifEmpty {
            (0..65535).map { (it shl 16) or base16 }
        }

        val newMatches = mutableListOf<Pair<Int, Int>>()

        for (candidateSeed in candidatesToTest) {
            var bestB = -1

            for (b in 15 downTo 0) {
                val costRand = LCG()
                costRand.setSeed(candidateSeed.toLong())

                val c0 = EnchantFunctions.getSimulatedCost(costRand, 0, b, enchantability)
                val c1 = EnchantFunctions.getSimulatedCost(costRand, 1, b, enchantability)
                val c2 = EnchantFunctions.getSimulatedCost(costRand, 2, b, enchantability)

                if (c0 == menu.enchantmentPower[0] && c1 == menu.enchantmentPower[1] && c2 == menu.enchantmentPower[2]) {
                    bestB = b
                    break
                }
            }

            if (bestB == -1) continue

            var isMatch = true
            for (i in validSlots) {
                val rand = LCG()
                rand.setSeed((candidateSeed + i).toLong())

                val results = EnchantFunctions.enchantTableSlot(
                    rng = rand,
                    enchantability = enchantability,
                    eligibleIds = eligibleEnchantments,
                    baseCost = menu.enchantmentPower[i],
                    isBook = isBook
                )

                if (results.isEmpty()) {
                    isMatch = false
                    break
                }

                val clueIndex = rand.nextInt(results.size)
                val predictedClue = results[clueIndex]
                val predictedId = registry.getRawId(predictedClue.enchantment)

                if (predictedId != menu.enchantmentId[i] || predictedClue.level != menu.enchantmentLevel[i]) {
                    isMatch = false
                    break
                }
            }

            if (isMatch) {
                newMatches.add(candidateSeed to bestB)
            }
        }

        possibleSeeds = newMatches.sortedByDescending { it.second }.map { it.first }

        return when {
            possibleSeeds.isEmpty() -> {
                lastLower16 = -1
                null
            }
            possibleSeeds.size == 1 -> {
                possibleSeeds.first()
            }
            else -> {
                null
            }
        }
    }
}