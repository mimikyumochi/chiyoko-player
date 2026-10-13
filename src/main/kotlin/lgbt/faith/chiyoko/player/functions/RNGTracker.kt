package lgbt.faith.chiyoko.player.functions

import lgbt.faith.chiyoko.player.rand.LCG
import lgbt.faith.chiyoko.player.sendOverlay
import net.minecraft.util.Formatting
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import kotlin.math.abs

// owns the cracked player rng state no matter which cracker found it
object RngTracker {
    private data class PlayerSnapshot(
        val entityId: Int,
        val dimension: Any,
        val x: Double, val y: Double, val z: Double,
        val health: Float,
        val absorption: Float,
        val food: Int,
        val xpLevel: Int,
        val xpProgress: Float,
        val usingItem: Boolean,
    )

    private const val POSITION_EPSILON = 1.0E-4
    private const val XP_ACTION_MS = 3000L

    // lcg state right after the last rng use we know about
    var state: LCG? = null
        private set

    // paper gives every entity one shared random so the player state cant be tracked
    var sharedRng = false
        private set

    private var lastSnapshot: PlayerSnapshot? = null
    private var xpActionDeadline = 0L

    // resets whenever something might have used player rng
    fun tick(mc: MinecraftClient) {
        val player = mc.player ?: return

        val snapshot = PlayerSnapshot(
            entityId = player.id,
            dimension = player.entityWorld.registryKey,
            x = player.x, y = player.y, z = player.z,
            health = player.health,
            absorption = player.absorptionAmount,
            food = player.hungerManager.foodLevel,
            xpLevel = player.experienceLevel,
            xpProgress = player.experienceProgress,
            usingItem = player.isUsingItem,
        )

        val last = lastSnapshot
        lastSnapshot = snapshot
        if (last == null) return

        val reason = when {
            last.entityId != snapshot.entityId || last.dimension != snapshot.dimension -> "respawned"
            abs(last.x - snapshot.x) > POSITION_EPSILON ||
                    abs(last.y - snapshot.y) > POSITION_EPSILON ||
                    abs(last.z - snapshot.z) > POSITION_EPSILON -> "moved"
            last.health != snapshot.health || last.absorption != snapshot.absorption -> "health"
            last.food != snapshot.food -> "hunger"
            last.xpLevel != snapshot.xpLevel || last.xpProgress != snapshot.xpProgress -> {
                // xp spent on an enchant or anvil we already stepped the rng for
                if (System.currentTimeMillis() <= xpActionDeadline) return
                "xp"
            }
            snapshot.usingItem -> "item_use"
            else -> return
        }
        reset(reason)
    }

    fun reset(reason: String? = null) {
        val hadProgress = DropCracker.hasObservations || state != null
        DropCracker.clearObservations()
        state = null
        // something else used the rng so whatever auto crack was doing is wasted
        if (reason != null) AutoCracker.cancel()
        if (reason != null && hadProgress) {
            sendOverlay("chiyoko-player.rng.reset", Formatting.YELLOW, Text.translatable("chiyoko-player.rng.reset.$reason"))
        }
    }

    fun clear() {
        reset()
        AutoCracker.cancel()
        sharedRng = false
        lastSnapshot = null
    }

    fun onStateCracked(cracked: LCG, advance: LCG.() -> Unit = {}) {
        if (sharedRng) return

        val known = state
        if (known != null) {
            if (known.copy().apply(advance).seed == cracked.seed) {
                state = cracked
                return
            }
            sendOverlay("chiyoko-player.rng.desynced", Formatting.YELLOW)
        }

        DropCracker.clearObservations()
        state = cracked
        sendOverlay("chiyoko-player.rng.cracked", Formatting.GREEN)
        AutoCracker.onCracked()
    }

    fun observe(matches: LCG.() -> Boolean): Boolean {
        val known = state ?: return false
        val next = known.copy()
        if (next.matches()) {
            state = next
            return true
        }
        reset()
        sendOverlay("chiyoko-player.rng.desynced", Formatting.YELLOW)
        return false
    }

    fun onXpAction(advance: LCG.() -> Unit) {
        val known = state
        if (known == null) {
            reset("xp")
            return
        }
        known.advance()
        xpActionDeadline = System.currentTimeMillis() + XP_ACTION_MS
    }

    fun onSharedRngDetected() {
        sharedRng = true
        reset()
        sendOverlay("chiyoko-player.rng.shared", Formatting.RED)
    }
}
