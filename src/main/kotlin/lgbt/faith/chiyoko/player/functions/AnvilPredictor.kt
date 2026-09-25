package lgbt.faith.chiyoko.player.functions

import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.client.Minecraft

object AnvilPredictor {
    sealed interface Prediction {
        // anvil wont chip for this many uses
        data class Safe(val uses: Int) : Prediction

        // anvil chips on this use unless you drop this many items first
        data class Chips(val drops: Int) : Prediction
    }

    // AnvilMenu onTake chips when player random nextFloat is under this
    private const val CHIP_CHANCE = 0.12f
    private const val MAX_USES = 999
    private const val MAX_DROPS = 64 * 36

    fun predict(): Prediction? {
        val player = Minecraft.getInstance().player ?: return null
        // creative never chips or rolls
        if (player.hasInfiniteMaterials()) return null
        val base = EnchantPredictor.entityLCG?.copy() ?: return null

        if (!chips(base.copy())) {
            val rolls = base.copy()
            var uses = 0
            while (uses < MAX_USES && !chips(rolls)) uses++
            return Prediction.Safe(uses)
        }

        // each drop uses 4 nextFloat calls so find the fewest drops that make the next roll safe
        val afterDrops = base.copy()
        for (drops in 1..MAX_DROPS) {
            repeat(4) { afterDrops.nextFloat() }
            if (!chips(afterDrops.copy())) return Prediction.Chips(drops)
        }
        return null
    }

    private fun chips(rand: LCG) = rand.nextFloat() < CHIP_CHANCE
}
