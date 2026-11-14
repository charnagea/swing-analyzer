package dev.swinganalyzer.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A grayscale crop of one video frame, in raw (un-normalized) pixel coordinates. */
class GrayCrop(val frameIndex: Int, val x0: Int, val y0: Int, val w: Int, val h: Int, val pixels: ByteArray) {
    init { require(pixels.size == w * h) { "Crop size mismatch" } }

    /** Bilinear sample at raw frame coordinates; NaN outside the crop. */
    fun sample(rx: Double, ry: Double): Double {
        val x = rx - x0; val y = ry - y0
        if (x < 0 || y < 0 || x > w - 1.001 || y > h - 1.001) return Double.NaN
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = x - xi; val fy = y - yi
        fun px(a: Int, b: Int) = (pixels[b * w + a].toInt() and 0xFF).toDouble()
        val top = px(xi, yi) * (1 - fx) + px(xi + 1, yi) * fx
        val bot = px(xi, yi + 1) * (1 - fx) + px(xi + 1, yi + 1) * fx
        return top * (1 - fy) + bot * fy
    }
}

/** Where to cut a crop for the shaft finder, in raw frame pixels. */
data class CropRequest(val frameIndex: Int, val x0: Int, val y0: Int, val w: Int, val h: Int)

data class ShaftReading(val frameIndex: Int, val angleDeg: Double, val score: Double)

/**
 * Finds the club shaft rising above the hands near the top of the swing.
 *
 * For each upward direction from the tracked hands, it walks along a ray and measures how much the ray
 * looks like a thin line: a pixel that is darker (or lighter) than the pixels a few pixels to either
 * side. The direction with the strongest thin-line response is the shaft. Angles are in normalized
 * coordinates, measured from +x (toward the ball or target) with y pointing up: 90° = straight up.
 * Tested on open-sky backgrounds; against clutter it may report nothing, and the app falls back to the hands.
 */
object ShaftFinder {

    fun cropRequests(n: NormalizedTrack, ev: SwingEvents): List<CropRequest> {
        if (ev.shot != ShotType.FULL) return emptyList()
        val from = max(ev.start, ev.handsHighest - (0.25 / n.dt).roundToInt())
        val to = min(ev.impact, (ev.handsStartDown ?: ev.handsHighest) + (0.1 / n.dt).roundToInt())
        val t = n.torso
        return (from..to).map { i ->
            val h = n.hands(i)
            val rx = n.toRawX(h.x)
            val x0 = (rx - 1.3 * t).roundToInt().coerceIn(0, n.width - 1)
            val x1 = (rx + 1.3 * t).roundToInt().coerceIn(0, n.width - 1)
            val y0 = (h.y - 1.3 * t).roundToInt().coerceIn(0, n.height - 1)
            val y1 = (h.y + 0.4 * t).roundToInt().coerceIn(0, n.height - 1)
            CropRequest(n.frames[i].index, x0, y0, max(1, x1 - x0 + 1), max(1, y1 - y0 + 1))
        }
    }

    fun read(n: NormalizedTrack, sampleIndex: Int, crop: GrayCrop): ShaftReading? {
        val hands = n.hands(sampleIndex)
        val t = n.torso
        val step = max(1.0, t / 64)
        val k = max(2.0, 0.03 * t)
        // Only the stretch of shaft next to the hands: further up the line may run past the clubhead.
        val d0 = 0.05 * t; val d1 = 0.5 * t
        // The wrists sit beside the grip, not on the shaft line, so the line may pass up to 0.3 T to the side.
        val maxOffset = 0.3 * t
        val offStep = max(1.0, t / 85)

        fun score(thetaDeg: Double, offset: Double): Pair<Double, Double> {
            val th = thetaDeg * PI / 180
            val ux = cos(th); val uy = -sin(th)
            val nx = sin(th); val ny = cos(th)
            val ox = hands.x + nx * offset; val oy = hands.y + ny * offset
            var sum = 0.0; var count = 0; var strong = 0
            var d = d0
            while (d <= d1) {
                val px = ox + ux * d; val py = oy + uy * d
                val ip = crop.sample(n.toRawX(px), py)
                val ia = crop.sample(n.toRawX(px + nx * k), py + ny * k)
                val ib = crop.sample(n.toRawX(px - nx * k), py - ny * k)
                if (!ip.isNaN() && !ia.isNaN() && !ib.isNaN()) {
                    val dark = min(ia - ip, ib - ip)
                    val light = min(ip - ia, ip - ib)
                    val r = max(0.0, max(dark, light))
                    sum += r; count++
                    if (r > 4) strong++
                }
                d += step
            }
            if (count < 8) return Pair(0.0, 0.0)
            return Pair(sum / count, strong.toDouble() / count)
        }

        var best = -1.0; var bestTheta = 0.0; var bestOff = 0.0; var bestCov = 0.0
        fun consider(th: Double, off: Double) {
            val (s, c) = score(th, off)
            if (s > best) { best = s; bestTheta = th; bestOff = off; bestCov = c }
        }
        var th = 15.0
        while (th <= 165.0) {
            var off = -maxOffset
            while (off <= maxOffset) { consider(th, off); off += offStep }
            th += 2.0
        }
        // Refine around the best coarse line.
        val (cTheta, cOff) = Pair(bestTheta, bestOff)
        th = cTheta - 2.0
        while (th <= cTheta + 2.0) {
            var off = cOff - offStep
            while (off <= cOff + offStep) { consider(th, off); off += offStep / 3 }
            th += 0.5
        }
        if (best < 6.0 || bestCov < 0.45) return null
        return ShaftReading(n.frames[sampleIndex].index, bestTheta, best)
    }

    /**
     * Club starts down: the first sample after the flattest shaft angle where the shaft is clearly
     * steeper (4°) than the three flattest readings around the top. Returns (top sample, fractional
     * sample where the downswing starts), or null if the readings don't show a clear turn.
     */
    fun clubTurn(n: NormalizedTrack, ev: SwingEvents, readings: Map<Int, Double>): Pair<Int, Double>? {
        if (readings.size < 5) return null
        val idx = readings.keys.sorted()
        val m = idx.minBy { readings.getValue(it) }
        val w = max(2, (0.15 / n.dt).roundToInt())
        val flat = idx.filter { kotlin.math.abs(it - m) <= w }.map { readings.getValue(it) }.sorted().take(3).let { it[it.size / 2] }
        val limit = flat + 4.0
        val down = idx.firstOrNull { it > m && readings.getValue(it) > limit } ?: return null
        val prev = down - 1
        val sub = if (prev in readings) {
            val a0 = readings.getValue(prev); val a1 = readings.getValue(down)
            if (a1 != a0) prev + ((limit - a0) / (a1 - a0)).coerceIn(0.0, 1.0) else down.toDouble()
        } else down.toDouble()
        // Sanity: the club must turn between the hands' highest point and shortly after they start down.
        val lo = ev.handsHighest - (0.25 / n.dt)
        val hi = (ev.handsStartDown ?: ev.impact) + (0.1 / n.dt)
        if (sub < lo || sub > hi || sub >= ev.impactSub) return null
        return Pair(m, sub)
    }
}
