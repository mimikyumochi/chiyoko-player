package lgbt.faith.chiyoko.player.functions

import com.seedfinding.latticg.JavaRandomReverser
import lgbt.faith.chiyoko.player.mixin.LocalPlayerAccessor
import lgbt.faith.chiyoko.player.pluralKey
import lgbt.faith.chiyoko.player.rand.LCG
import lgbt.faith.chiyoko.player.sendOverlay
import net.minecraft.util.Formatting
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object DropCracker {
    // rotation is null when the player turned just before the drop
    data class DropObservation(val xRot: Float?, val yRot: Float?, val movement: Vec3d)

    private const val BITS_TO_SOLVE = 50.0
    private const val MAX_CANDIDATES = 256L
    private const val ROTATION_SETTLE_MS = 1000L
    private const val POSITION_EPSILON = 1.0E-4
    private const val SHARED_RNG_MAX_GAP = 32
    private const val SHARED_RNG_MAX_ANGLE = 0.5
    private const val SHARED_RNG_LENGTH_EPSILON = 1.5E-4
    private const val SHARED_RNG_HITS = 2

    private val LOGGER = LoggerFactory.getLogger("chiyoko-player")

    private val observations = mutableListOf<DropObservation>()
    private var generation = 0
    private var solving = false
    private var lastSentRot: Pair<Float, Float>? = null
    private var lastRotChangeMs = 0L
    // lastRotChangeMs at each drop we threw ourselves, oldest first
    private val sentDropRotStamps = ArrayDeque<Long>()
    private var sharedRngHits = 0

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "chiyoko-drop-cracker").apply { isDaemon = true } }

    val hasObservations: Boolean get() = observations.isNotEmpty()

    val rotationSettled: Boolean get() = System.currentTimeMillis() - lastRotChangeMs >= ROTATION_SETTLE_MS

    // the server already has the rotation the player has now
    fun rotationSent(player: ClientPlayerEntity): Boolean {
        val accessor = player as LocalPlayerAccessor
        return accessor.chiyoko_getXRotLast() == player.pitch && accessor.chiyoko_getYRotLast() == player.yaw
    }

    // call right after throwing an item, once rotationSent is true
    fun onDropSent() {
        sentDropRotStamps.addLast(lastRotChangeMs)
    }

    // tracks when the rotation the server knows about last changed
    fun tick(mc: MinecraftClient) {
        val player = mc.player ?: return

        val accessor = player as LocalPlayerAccessor
        val rot = accessor.chiyoko_getXRotLast() to accessor.chiyoko_getYRotLast()
        if (rot != lastSentRot) {
            lastSentRot = rot
            lastRotChangeMs = System.currentTimeMillis()
        }
    }

    // also drops any solve still running since its drops are gone
    fun clearObservations() {
        observations.clear()
        generation++
    }

    fun clear() {
        sentDropRotStamps.clear()
        clearObservations()
        sharedRngHits = 0
        lastSentRot = null
    }

    // returns true if the item was dropped by us
    fun onItemSpawned(x: Double, y: Double, z: Double, uuid: UUID, movement: Vec3d): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        val handY = player.eyeY - 0.3f
        if (abs(x - player.x) > POSITION_EPSILON || abs(y - handY) > POSITION_EPSILON || abs(z - player.z) > POSITION_EPSILON) {
            return false
        }

        // if we turned recently the server rotation is unknown so the drop only counts as 4 skipped calls
        val accessor = player as LocalPlayerAccessor
        // a drop we threw after the rotation was sent is exact as long as the camera hasnt moved since
        val sentStamp = sentDropRotStamps.removeFirstOrNull()
        val settled = rotationSettled || sentStamp == lastRotChangeMs
        val observation = if (settled) {
            DropObservation(
                MathHelper.wrapDegrees(accessor.chiyoko_getXRotLast()),
                MathHelper.wrapDegrees(accessor.chiyoko_getYRotLast()),
                movement
            )
        } else {
            DropObservation(null, null, movement)
        }

        LOGGER.info(
            "own drop uuid={} movement=({}, {}, {}) rot={} settled={}",
            uuid, movement.x, movement.y, movement.z,
            "${accessor.chiyoko_getXRotLast()}/${accessor.chiyoko_getYRotLast()}", settled
        )

        if (RngTracker.sharedRng) return true
        // one match can be chance so wait for a second one
        if (usesSharedRng(uuid, movement, player.pitch, player.yaw) && ++sharedRngHits >= SHARED_RNG_HITS) {
            RngTracker.onSharedRngDetected()
            return true
        }

        onOwnDrop(observation)
        return true
    }

    // paper gives every entity one shared random so other entities use it between drops
    // the item uuid comes from that random right before the drop so a matching uuid means shared rng
    // this doesnt need the exact server rotation since the base velocity is just 0.3 times the look vector
    private fun usesSharedRng(uuid: UUID, movement: Vec3d, xRot: Float, yRot: Float): Boolean {
        val afterUuid = stateFromUuid(uuid)
        if (afterUuid == null) {
            LOGGER.info("shared rng check: uuid not from a java random")
            return false
        }

        var best = Double.MAX_VALUE
        var bestGap = -1
        for (gap in 0..SHARED_RNG_MAX_GAP) {
            val rand = afterUuid.copy()
            repeat(gap) { rand.next(32) }

            val dir = rand.nextFloat() * (Math.PI * 2).toFloat()
            val pow2 = 0.02f * rand.nextFloat()
            val wobble = (rand.nextFloat() - rand.nextFloat()) * 0.1f

            // whats left after removing the random parts should be 0.3 times the look vector
            val bx = movement.x - Math.cos(dir.toDouble()) * pow2
            val by = movement.y - wobble - 0.1f
            val bz = movement.z - Math.sin(dir.toDouble()) * pow2
            val length = sqrt(bx * bx + by * by + bz * bz)
            if (abs(length - 0.3) > SHARED_RNG_LENGTH_EPSILON) continue

            val impliedXRot = Math.toDegrees(asin((-by / 0.3).coerceIn(-1.0, 1.0)))
            val impliedYRot = Math.toDegrees(atan2(-bx, bz))
            val angle = max(abs(MathHelper.wrapDegrees(impliedXRot - xRot.toDouble())), abs(MathHelper.wrapDegrees(impliedYRot - yRot.toDouble())))
            if (angle < best) {
                best = angle
                bestGap = gap
            }
        }

        LOGGER.info("shared rng check: best gap={} angle off={}", bestGap, best)
        return best <= SHARED_RNG_MAX_ANGLE
    }

    // cracks the lcg state after Mth createInsecureUUID from the uuid it made
    private fun stateFromUuid(uuid: UUID): LCG? {
        val most = uuid.mostSignificantBits
        val least = uuid.leastSignificantBits
        val uuidMask = 0xFFFF0FFFL.toInt()

        // nextLong adds the low int as signed so a negative low int borrows from the high int
        val high = (most ushr 32).toInt() + if (most and 0x80000000L != 0L) 1 else 0
        for (low in 0..0xFFFF) {
            val rand = LCG(((high.toLong() and 0xFFFFFFFFL) shl 16) or low.toLong())
            if (rand.next(32) and uuidMask != most.toInt() and uuidMask) continue

            val second = (rand.nextInt().toLong() shl 32) + rand.nextInt()
            if (second and 0x3FFFFFFFFFFFFFFFL == least and 0x3FFFFFFFFFFFFFFFL) return rand
        }
        return null
    }

    private fun onOwnDrop(observation: DropObservation) {
        if (RngTracker.state != null && RngTracker.observe { simulateAndCompare(this, observation) }) return

        observations.add(observation)
        trySolve()
    }

    private fun trySolve() {
        if (solving || RngTracker.state != null) return

        val snapshot = observations.toList()
        val bits = snapshot.sumOf { constraintsFor(it)?.bits ?: 0.0 }
        if (bits < BITS_TO_SOLVE) {
            sendOverlay(pluralKey("chiyoko-player.rng.progress", snapshot.size), Formatting.AQUA, snapshot.size, bits.toInt())
            return
        }

        solving = true
        val solveGeneration = generation
        sendOverlay("chiyoko-player.rng.solving", Formatting.AQUA)

        executor.execute {
            val candidates = try {
                solve(snapshot)
            } catch (e: Exception) {
                e.printStackTrace()
                LongArray(0)
            }

            val mc = MinecraftClient.getInstance()
            mc.execute { onSolved(solveGeneration, snapshot.size, candidates) }
        }
    }

    private fun onSolved(solveGeneration: Int, solvedCount: Int, candidates: LongArray) {
        solving = false
        if (solveGeneration != generation) {
            trySolve()
            return
        }

        // also checks drops that came in while solving
        val matches = candidates.asList().mapNotNull { seed ->
            val rand = LCG(seed)
            if (observations.all { simulateAndCompare(rand, it) }) rand else null
        }

        LOGGER.info("solved {} drops: {} lattice candidates, {} match every drop", solvedCount, candidates.size, matches.size)

        when {
            matches.size == 1 -> {
                RngTracker.onStateCracked(matches.first())
            }
            matches.isEmpty() && candidates.size < MAX_CANDIDATES -> {
                // nothing matched so the first drop was probably bad
                observations.removeFirstOrNull()
                sendOverlay("chiyoko-player.rng.inconsistent", Formatting.YELLOW)
                trySolve()
            }
            else -> {
                sendOverlay(pluralKey("chiyoko-player.rng.candidates", matches.size), Formatting.AQUA, matches.size)
                if (observations.size > solvedCount) trySolve()
            }
        }
    }

    // returns lcg states from before the first drop
    private fun solve(drops: List<DropObservation>): LongArray {
        val reverser = JavaRandomReverser(ArrayList())
        for (drop in drops) {
            val c = constraintsFor(drop)
            if (c?.dir != null) reverser.addFloatRange(c.dir) else reverser.consumeNextFloatCalls(1)
            if (c != null) reverser.addFloatRange(c.pow2) else reverser.consumeNextFloatCalls(1)
            reverser.consumeNextFloatCalls(2)
        }
        return reverser.findAllValidSeeds().limit(MAX_CANDIDATES).toArray()
    }

    private fun JavaRandomReverser.addFloatRange(range: ClosedFloatingPointRange<Double>) {
        val minBits = max(0L, ceil(range.start * (1 shl 24)).toLong())
        val maxBits = min((1L shl 24) - 1, floor(range.endInclusive * (1 shl 24)).toLong())
        addMeasuredSeed(minBits shl 24, (maxBits shl 24) or 0xFFFFFFL)
    }

    private class Constraints(
        val dir: ClosedFloatingPointRange<Double>?,
        val pow2: ClosedFloatingPointRange<Double>,
    ) {
        val bits: Double get() = info(pow2) + (dir?.let(::info) ?: 0.0)

        private fun info(range: ClosedFloatingPointRange<Double>) =
            -ln(max(range.endInclusive - range.start, 1.0E-9)) / ln(2.0)
    }

    // velocity to nextFloat ranges for dir and pow2
    private fun constraintsFor(drop: DropObservation): Constraints? {
        val xRot = drop.xRot ?: return null
        val yRot = drop.yRot ?: return null
        val (baseX, _, baseZ) = baseVelocity(xRot, yRot)

        // px and pz are cos dir and sin dir times pow2
        val px = drop.movement.x - baseX
        val pz = drop.movement.z - baseZ
        val error = quantization(drop.movement) * 1.05 + 1.0E-7
        val radius = hypot(px, pz)
        val radiusError = error * sqrt(2.0)

        val pow2 = max(0.0, (radius - radiusError) / 0.02 - 1.0E-6)..min(1.0, (radius + radiusError) / 0.02 + 1.0E-6)

        var dir: ClosedFloatingPointRange<Double>? = null
        if (radius > radiusError * 1.5) {
            var angle = atan2(pz, px)
            if (angle < 0) angle += Math.PI * 2
            val spread = asin(radiusError / radius)
            val lo = (angle - spread) / (Math.PI * 2) - 1.0E-6
            val hi = (angle + spread) / (Math.PI * 2) + 1.0E-6
            // ranges that wrap around 0 are skipped
            if (lo >= 0.0 && hi < 1.0) dir = lo..hi
        }

        return Constraints(dir, pow2)
    }

    // same as LivingEntity createItemStackToDrop and uses 4 nextFloat calls
    private fun simulateAndCompare(rand: LCG, drop: DropObservation): Boolean {
        val dir = rand.nextFloat() * (Math.PI * 2).toFloat()
        val pow2 = 0.02f * rand.nextFloat()
        val wobble = (rand.nextFloat() - rand.nextFloat()) * 0.1f

        val xRot = drop.xRot ?: return true
        val yRot = drop.yRot ?: return true
        val (baseX, baseY, baseZ) = baseVelocity(xRot, yRot)

        val vx = baseX + Math.cos(dir.toDouble()) * pow2
        val vy = (baseY + wobble).toDouble()
        val vz = baseZ + Math.sin(dir.toDouble()) * pow2

        val tolerance = quantization(drop.movement) * 1.5
        return abs(vx - drop.movement.x) <= tolerance &&
                abs(vy - drop.movement.y) <= tolerance &&
                abs(vz - drop.movement.z) <= tolerance
    }

    private fun baseVelocity(xRot: Float, yRot: Float): Triple<Double, Float, Double> {
        val sinX = MathHelper.sin((xRot * (Math.PI / 180.0).toFloat()).toDouble())
        val cosX = MathHelper.cos((xRot * (Math.PI / 180.0).toFloat()).toDouble())
        val sinY = MathHelper.sin((yRot * (Math.PI / 180.0).toFloat()).toDouble())
        val cosY = MathHelper.cos((yRot * (Math.PI / 180.0).toFloat()).toDouble())
        return Triple(
            (-sinY * cosX * 0.3f).toDouble(),
            -sinX * 0.3f + 0.1f,
            (cosY * cosX * 0.3f).toDouble()
        )
    }

    // max LpVec3 error for this vector
    private fun quantization(movement: Vec3d): Double {
        val scale = ceil(max(abs(movement.x), max(abs(movement.y), abs(movement.z)))).coerceAtLeast(1.0)
        return scale / 32766.0
    }
}
