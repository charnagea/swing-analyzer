package dev.swinganalyzer.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Key positions as sample indices into a [NormalizedTrack]. Fractional values are refined between frames.
 * For putts and chips, "top" means the end of the backstroke.
 */
data class SwingEvents(
    val shot: ShotType,
    val address: Int,
    val start: Int,
    val startSub: Double,
    val handsHighest: Int,
    val handsStartDown: Int?,
    val impact: Int,
    val impactSub: Double,
    val finish: Int?,
    val pauseAtTop: Double,
    /** Filled in after the shaft finder runs (full swings only). */
    val clubTop: Int? = null,
    val clubDownSub: Double? = null,
    val backLength: Double? = null,
    val throughLength: Double? = null,
) {
    /** The frame used as "the top" for posture checks. */
    val top: Int get() = clubTop ?: handsHighest
}

class SwingNotFound(message: String) : Exception(message)

object EventFinder {

    /** Hand speed per sample, in torso lengths per 1/30 s (so thresholds don't depend on frame rate). */
    private fun handSpeed(n: NormalizedTrack): DoubleArray {
        val k = n.torso * n.dt * 30
        return DoubleArray(n.size) { i -> if (i == 0) 0.0 else n.hands(i).dist(n.hands(i - 1)) / k }
    }

    fun find(n: NormalizedTrack, shot: ShotType): SwingEvents =
        if (shot == ShotType.FULL) fullSwing(n) else shortGame(n, shot)

    private fun fullSwing(n: NormalizedTrack): SwingEvents {
        val speed = handSpeed(n)
        val t = n.torso
        val earlyN = max(3, (0.3 / n.dt).roundToInt()).coerceAtMost(n.size)
        val earlyY = (0 until earlyN).map { n.hands(it).y }.average()

        // First movement: find where the hands have clearly risen, then step back to the last still frame.
        val risen = (0 until n.size).firstOrNull { earlyY - n.hands(it).y > 0.15 * t }
            ?: throw SwingNotFound("No backswing found: the hands never rose.")
        var start = risen
        while (start > 1 && speed[start] > 0.008) start--
        start = (start + 1).coerceAtMost(risen)

        // Highest point of the hands, stopping once they've dropped half a torso below it.
        var top = start
        var i = start
        while (i < n.size && n.hands(i).y < n.hands(top).y + 0.5 * t) {
            if (n.hands(i).y < n.hands(top).y) top = i
            i++
        }
        val base = n.hands(max(0, start - 1))

        // Impact: lowest hand position near address height within one second after the top.
        val searchEnd = min(n.size - 1, top + (1.0 / n.dt).roundToInt())
        val cands = (top + 1..searchEnd).filter { kotlin.math.abs(n.hands(it).y - base.y) < 0.5 * t }
        if (cands.isEmpty()) throw SwingNotFound("No impact found after the top of the swing.")
        val imp = cands.maxBy { n.hands(it).y }
        val impSub = if (imp in 1 until n.size - 1) {
            val y0 = n.hands(imp - 1).y; val y1 = n.hands(imp).y; val y2 = n.hands(imp + 1).y
            val den = y0 - 2 * y1 + y2
            if (den != 0.0) imp + (0.5 * (y0 - y2) / den).coerceIn(-0.5, 0.5) else imp.toDouble()
        } else imp.toDouble()

        // Hands start down: first clear downward movement after the highest point.
        val down = (top + 1 until imp).firstOrNull {
            (n.hands(it).y - n.hands(it - 1).y) / (t * n.dt * 30) > 0.03
        }

        // Finish: hand speed stays low for 0.1 s after impact.
        val k = max(2, (0.1 / n.dt).roundToInt())
        val fin = (imp + 1 until n.size - k).firstOrNull { j -> (j until j + k).all { speed[it] < 0.025 } }

        // Pause at the top: how long the hands stay nearly still around their highest point.
        var a = top; var b = top
        while (a > start && speed[a] < 0.025) a--
        while (b < imp && speed[b + 1] < 0.025) b++
        val pause = n.t(b) - n.t(a)

        return SwingEvents(ShotType.FULL, max(0, start - 1), start, start - 0.5, top, down, imp, impSub, fin, pause)
    }

    private fun shortGame(n: NormalizedTrack, shot: ShotType): SwingEvents {
        val speed = handSpeed(n)
        val t = n.torso
        val earlyN = max(3, (0.3 / n.dt).roundToInt()).coerceAtMost(n.size)
        val early = (0 until earlyN).map { n.hands(it) }
        val earlyP = Vec(early.map { it.x }.average(), early.map { it.y }.average())
        val disp = DoubleArray(n.size) { n.hands(it).dist(earlyP) / t }

        val moved = (0 until n.size - 3).firstOrNull { disp[it] > 0.04 && disp[it + 1] > disp[it] && disp[it + 2] > disp[it + 1] }
            ?: throw SwingNotFound("No stroke found: the hands never moved away from address.")
        var start = moved
        while (start > 1 && speed[start] > 0.004) start--
        start = (start + 1).coerceAtMost(moved)
        val base = n.hands(max(0, start - 1))
        val fromBase = DoubleArray(n.size) { n.hands(it).dist(base) / t }

        // End of backstroke: farthest point from address before the hands come back.
        var top = start
        var i = start
        while (i < n.size && fromBase[i] > fromBase[top] - max(0.03, 0.4 * fromBase[top])) {
            if (fromBase[i] > fromBase[top]) top = i
            i++
        }
        // Impact: hands back at their address position.
        val searchEnd = min(n.size - 1, top + (1.0 / n.dt).roundToInt())
        if (searchEnd <= top) throw SwingNotFound("The clip ends before the stroke does.")
        val imp = (top + 1..searchEnd).minBy { fromBase[it] }
        val impSub = if (imp in 1 until n.size - 1) {
            val d0 = fromBase[imp - 1]; val d1 = fromBase[imp]; val d2 = fromBase[imp + 1]
            val den = d0 - 2 * d1 + d2
            if (den > 0) imp + (0.5 * (d0 - d2) / den).coerceIn(-0.5, 0.5) else imp.toDouble()
        } else imp.toDouble()

        val k = max(2, (0.1 / n.dt).roundToInt())
        val fin = (imp + 1 until n.size - k).firstOrNull { j -> (j until j + k).all { speed[it] < 0.01 } }
        val throughEnd = fin ?: (n.size - 1)
        val through = (imp..throughEnd).maxOf { fromBase[it] }

        return SwingEvents(shot, max(0, start - 1), start, start - 0.5, top, null, imp, impSub, fin, 0.0,
            backLength = fromBase[top], throughLength = through)
    }
}
