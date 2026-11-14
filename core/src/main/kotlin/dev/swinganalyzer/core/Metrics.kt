package dev.swinganalyzer.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A measured number with how far the landmarks behind it can be trusted (0–1). */
data class Measured(val value: Double, val confidence: Double)

/**
 * Computes the metrics named in knowledge.json from a normalized track and its key positions.
 * Distances are in torso lengths (T); angles in degrees. Conventions follow swing-knowledge-base.md §11.
 */
object Metrics {

    private fun deg(rad: Double) = rad * 180 / Math.PI

    /** Interior angle at b, in degrees. */
    private fun angle(a: Vec, b: Vec, c: Vec): Double {
        val u = a - b; val w = c - b
        val cos = ((u.x * w.x + u.y * w.y) / (u.len() * w.len())).coerceIn(-1.0, 1.0)
        return deg(acos(cos))
    }

    /** Trunk forward bend from vertical (DTL), positive toward the ball. */
    private fun bend(n: NormalizedTrack, i: Int): Double {
        val v = n.shoulders(i) - n.hips(i)
        return deg(atan2(v.x, -v.y))
    }

    /** Trunk lean away from the target (FO), positive = shoulders behind the hips. */
    private fun tiltAway(n: NormalizedTrack, i: Int): Double {
        val v = n.shoulders(i) - n.hips(i)
        return deg(atan2(-v.x, -v.y))
    }

    private fun kneeFlex(n: NormalizedTrack, i: Int, right: Boolean): Double =
        if (right) 180 - angle(n.p(i, Lm.R_HIP), n.p(i, Lm.R_KNEE), n.p(i, Lm.R_ANKLE))
        else 180 - angle(n.p(i, Lm.L_HIP), n.p(i, Lm.L_KNEE), n.p(i, Lm.L_ANKLE))

    private fun visAvg(n: NormalizedTrack, frames: Iterable<Int>, vararg lms: Int) =
        frames.map { n.vis(it, *lms) }.average()

    fun compute(n: NormalizedTrack, ev: SwingEvents, view: CameraView): Map<String, Measured> {
        val out = LinkedHashMap<String, Measured>()
        val t = n.torso
        val addrFrames = (max(0, ev.start - (0.3 / n.dt).roundToInt())..max(0, ev.start - 1)).toList()
        fun avg(f: (Int) -> Double) = addrFrames.map(f).average()
        fun addrPoint(f: (Int) -> Vec) = Vec(avg { f(it).x }, avg { f(it).y })
        val top = ev.top
        val imp = ev.impactSub.roundToInt().coerceIn(0, n.size - 1)
        val fin = ev.finish
        val hipA = addrPoint { n.hips(it) }
        val headA = addrPoint { n.head(it) }
        val torsoV = listOf(Lm.L_SHOULDER, Lm.R_SHOULDER, Lm.L_HIP, Lm.R_HIP)

        if (ev.shot == ShotType.FULL) {
            if (view == CameraView.DOWN_THE_LINE) {
                val bendA = avg { bend(n, it) }
                val trunkConf = visAvg(n, addrFrames, *torsoV.toIntArray())
                out["address_bend"] = Measured(bendA, trunkConf)
                out["address_knee"] = Measured(avg { (kneeFlex(n, it, true) + kneeFlex(n, it, false)) / 2 },
                    visAvg(n, addrFrames, Lm.L_HIP, Lm.R_HIP, Lm.L_KNEE, Lm.R_KNEE, Lm.L_ANKLE, Lm.R_ANKLE))
                out["address_hands"] = Measured(avg { (n.hands(it).x - n.shoulders(it).x) / t },
                    visAvg(n, addrFrames, Lm.L_WRIST, Lm.R_WRIST, Lm.L_SHOULDER, Lm.R_SHOULDER))
                out["bend_lost_top"] = Measured(bendA - bend(n, top), min(trunkConf, n.vis(top, *torsoV.toIntArray())))
                out["head_rise_top"] = Measured((headA.y - n.head(top).y) / t, n.vis(top, Lm.L_EAR, Lm.R_EAR))
                out["hip_to_ball_top"] = Measured((n.hips(top).x - hipA.x) / t, n.vis(top, Lm.L_HIP, Lm.R_HIP))
                if (bendA > 10) {
                    val ls = n.p(top, Lm.L_SHOULDER); val rs = n.p(top, Lm.R_SHOULDER)
                    val tilt = deg(atan2(ls.y - rs.y, abs(ls.x - rs.x)))
                    out["shoulder_plane_ratio"] = Measured(tilt / bendA, n.vis(top, Lm.L_SHOULDER, Lm.R_SHOULDER))
                }
                val trailA = avg { kneeFlex(n, it, true) }
                if (trailA > 8) out["trail_knee_kept"] = Measured(kneeFlex(n, top, true) / trailA,
                    n.vis(top, Lm.R_HIP, Lm.R_KNEE, Lm.R_ANKLE))
                out["hip_to_ball_impact"] = Measured((n.hips(imp).x - hipA.x) / t, n.vis(imp, Lm.L_HIP, Lm.R_HIP))
                out["bend_lost_impact"] = Measured(bendA - bend(n, imp), n.vis(imp, *torsoV.toIntArray()))
                out["head_rise_impact"] = Measured((headA.y - n.head(imp).y) / t, n.vis(imp, Lm.L_EAR, Lm.R_EAR))
                ottHandPath(n, ev, addrFrames)?.let { out["ott_hand_path"] = it }
            } else {
                val ankleW = avg { abs(n.p(it, Lm.L_ANKLE).x - n.p(it, Lm.R_ANKLE).x) }
                val shW = avg { abs(n.p(it, Lm.L_SHOULDER).x - n.p(it, Lm.R_SHOULDER).x) }
                if (shW > 1) out["stance_ratio"] = Measured(ankleW / shW,
                    visAvg(n, addrFrames, Lm.L_ANKLE, Lm.R_ANKLE, Lm.L_SHOULDER, Lm.R_SHOULDER))
                out["address_tilt"] = Measured(avg { tiltAway(n, it) }, visAvg(n, addrFrames, *torsoV.toIntArray()))
                var sway = (hipA.x - n.hips(top).x) / t
                val trailAnkleX = avg { n.p(it, Lm.R_ANKLE).x }
                if (n.hips(top).x < trailAnkleX) sway = max(sway, 0.21) // hips outside the trail foot
                out["sway_top"] = Measured(sway, n.vis(top, Lm.L_HIP, Lm.R_HIP))
                out["reverse_spine_top"] = Measured(-tiltAway(n, top), n.vis(top, *torsoV.toIntArray()))
                out["head_to_target_top"] = Measured((n.head(top).x - headA.x) / t, n.vis(top, Lm.L_EAR, Lm.R_EAR))
                out["hip_shift_impact"] = Measured((n.hips(imp).x - hipA.x) / t, n.vis(imp, Lm.L_HIP, Lm.R_HIP))
                val ls = n.p(imp, Lm.L_SHOULDER); val rs = n.p(imp, Lm.R_SHOULDER)
                out["shoulder_tilt_impact"] = Measured(deg(atan2(rs.y - ls.y, abs(rs.x - ls.x))),
                    n.vis(imp, Lm.L_SHOULDER, Lm.R_SHOULDER))
                out["head_to_target_impact"] = Measured((n.head(imp).x - headA.x) / t, n.vis(imp, Lm.L_EAR, Lm.R_EAR))
                out["lead_elbow_top"] = Measured(180 - angle(n.p(top, Lm.L_SHOULDER), n.p(top, Lm.L_ELBOW), n.p(top, Lm.L_WRIST)),
                    n.vis(top, Lm.L_SHOULDER, Lm.L_ELBOW, Lm.L_WRIST))
                if (fin != null) {
                    val la = n.p(fin, Lm.L_ANKLE).x; val ra = n.p(fin, Lm.R_ANKLE).x
                    if (la - ra > 1) out["finish_over_lead"] = Measured((n.hips(fin).x - ra) / (la - ra),
                        n.vis(fin, Lm.L_HIP, Lm.R_HIP, Lm.L_ANKLE, Lm.R_ANKLE))
                    out["finish_lean_back"] = Measured(tiltAway(n, fin), n.vis(fin, *torsoV.toIntArray()))
                }
            }
            // Both views: finish.
            val heelA = avg { n.p(it, Lm.R_HEEL).y }
            if (fin != null) {
                val settle = min(n.size - 1, fin + (0.3 / n.dt).roundToInt())
                out["trail_heel_lift"] = Measured((heelA - n.p(settle, Lm.R_HEEL).y) / t, n.vis(settle, Lm.R_HEEL))
                // Hips only: the head normally keeps turning to watch the ball after the finish.
                val still = (fin until settle).all { j -> n.hips(j + 1).dist(n.hips(j)) / (t * n.dt * 30) < 0.012 }
                out["finish_held"] = Measured(if (still && settle - fin >= (0.25 / n.dt).roundToInt()) 1.0 else 0.0, 0.8)
            } else {
                out["finish_held"] = Measured(0.0, 0.6)
            }
            ev.clubDownSub?.let { cd ->
                val back = n.timeAt(cd) - n.timeAt(ev.startSub)
                val down = n.timeAt(ev.impactSub) - n.timeAt(cd)
                if (back > 0 && down > 0) {
                    out["tempo_ratio"] = Measured(back / down, 0.95)
                    out["swing_time"] = Measured(back + down, 0.95)
                }
            }
        } else {
            val end = min(n.size - 1, ev.impact + (0.15 / n.dt).roundToInt())
            val window = ev.start..end
            out["head_move"] = Measured(window.maxOf { n.head(it).dist(headA) } / t, visAvg(n, window, Lm.L_EAR, Lm.R_EAR))
            out["hip_move"] = Measured(window.maxOf { n.hips(it).dist(hipA) } / t, visAvg(n, window, Lm.L_HIP, Lm.R_HIP))
            val back = n.timeAt(ev.top.toDouble()) - n.timeAt(ev.startSub)
            val down = n.timeAt(ev.impactSub) - n.timeAt(ev.top.toDouble())
            if (back > 0 && down > 0) out["tempo_ratio"] = Measured(back / down, 0.8)
            if (ev.backLength != null && ev.throughLength != null && ev.backLength > 0.02)
                out["through_back"] = Measured(ev.throughLength / ev.backLength, visAvg(n, window, Lm.L_WRIST, Lm.R_WRIST))
        }
        return out
    }

    /**
     * Possible over-the-top: at heights from shoulder level down to 30% of the way to the hips, compare
     * where the hands cross on the way down with where they crossed on the way up. Positive = outside
     * (toward the ball). Lower in the downswing the hands normally move out, so only this band is used.
     */
    private fun ottHandPath(n: NormalizedTrack, ev: SwingEvents, addr: List<Int>): Measured? {
        val shY = addr.map { n.shoulders(it).y }.average()
        val hipY = addr.map { n.hips(it).y }.average()
        fun crossing(range: IntProgression, y: Double, last: Boolean): Double? {
            val xs = ArrayList<Double>()
            for (i in range) {
                if (i <= 0) continue
                val a = n.hands(i - 1); val b = n.hands(i)
                if ((a.y - y) * (b.y - y) <= 0 && a.y != b.y) xs.add(a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x))
            }
            return if (xs.isEmpty()) null else if (last) xs.last() else xs.first()
        }
        val diffs = listOf(0.0, 0.1, 0.2, 0.3).mapNotNull { f ->
            val y = shY + f * (hipY - shY)
            val up = crossing(ev.start + 1..ev.handsHighest, y, last = true)
            val dn = crossing(ev.handsHighest + 1..ev.impact, y, last = false)
            if (up != null && dn != null) (dn - up) / n.torso else null
        }
        if (diffs.size < 2) return null
        val frames = ev.handsHighest..min(n.size - 1, ev.impact)
        return Measured(diffs.average(), frames.map { n.vis(it, Lm.L_WRIST, Lm.R_WRIST) }.average())
    }
}
