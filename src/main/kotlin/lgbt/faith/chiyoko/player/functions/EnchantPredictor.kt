package lgbt.faith.chiyoko.player.functions

import lgbt.faith.chiyoko.player.ItemEnchantData
import lgbt.faith.chiyoko.player.rand.LCG
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class EnchantTarget(
    val enchantment: Enchantment,
    val level: Int
)

object EnchantPredictor {
    var entityLCG: LCG? = null

    data class Result(val drops: Int, val bookshelves: Int, val slot: Int)

    sealed interface Outcome {
        data class Found(val result: Result) : Outcome
        data object NotFound : Outcome
        data object TimedOut : Outcome
        data object Failed : Outcome
        // the rng changed while predicting so the result is for an old state
        data object Stale : Outcome
    }

    enum class Start { STARTED, NOT_CRACKED, BUSY }

    private const val TIMEOUT_MS = 10_000L

    private val LOGGER = LoggerFactory.getLogger("chiyoko-player")

    // one prediction at a time on its own thread so the game doesnt freeze
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        SynchronousQueue(),
        { Thread(it, "chiyoko-enchant-predictor").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    private class TimedOutException : RuntimeException(null, null, false, false)

    // onDone runs on the main thread
    fun predictAsync(item: Item, targets: List<EnchantTarget>, onDone: (Outcome) -> Unit): Start {
        val base = entityLCG?.copy() ?: return Start.NOT_CRACKED

        try {
            executor.execute {
                val outcome = try {
                    val result = predict(base.copy(), item, targets, deadline = System.currentTimeMillis() + TIMEOUT_MS)
                    if (result != null) Outcome.Found(result) else Outcome.NotFound
                } catch (_: TimedOutException) {
                    Outcome.TimedOut
                } catch (e: Exception) {
                    LOGGER.error("enchant prediction failed", e)
                    Outcome.Failed
                }

                val mc = Minecraft.getInstance()
                mc.execute {
                    onDone(if (outcome is Outcome.Found && entityLCG?.seed != base.seed) Outcome.Stale else outcome)
                }
            }
        } catch (_: RejectedExecutionException) {
            return Start.BUSY
        }
        return Start.STARTED
    }

    private fun predict(base: LCG, item: Item, targets: List<EnchantTarget>, maxDrops: Int = 4096, deadline: Long): Result? {
        val (enchantability, eligible) = ItemEnchantData.of(item)

        // state after n item throws
        val afterDrops = base.copy()
        for (n in 0..maxDrops) {
            if (System.currentTimeMillis() > deadline) throw TimedOutException()
            if (n > 0) repeat(4) { afterDrops.nextFloat() }
            val entity = afterDrops.copy()

            val xpSeed = entity.nextInt()
            val xpSeedLong = xpSeed.toLong()

            for (bookshelves in 15 downTo 0) {
                val costRng = LCG()
                costRng.setSeed(xpSeedLong)

                val costs = IntArray(3) { i ->
                    EnchantFunctions.getSimulatedCost(costRng, i, bookshelves, enchantability)
                }

                for (slot in 2 downTo 0) {
                    val cost = costs[slot]
                    if (cost <= 0) continue

                    val rng = LCG()
                    rng.setSeed((xpSeed + slot).toLong())
                    val predicted = EnchantFunctions.enchantTableSlot(
                        rng, enchantability, eligible, cost, item == Items.BOOK
                    )

                    val matched = predicted.size >= targets.size && targets.all { target ->
                        predicted.any {
                            it.enchantment.registeredName.replace("minecraft:", "") == target.enchantment.id &&
                                    it.level == target.level
                        }
                    }
                    if (matched) return Result(n, bookshelves, slot)
                }
            }
        }
        return null
    }
}