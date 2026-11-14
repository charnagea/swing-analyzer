package dev.swinganalyzer.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A pose track rearranged so every later step can assume one geometry:
 * - right-handed golfer (left-handers are mirrored and their left/right labels swapped);
 * - down-the-line: the ball is toward +x; face-on: the target is toward +x;
 * - y grows downward (image convention).
 * [toRawX] maps a normalized x back to the original frame, for drawing and for the shaft finder.
 */
class NormalizedTrack(
    val frames: List<PoseFrame>,
    val width: Int,
    val height: Int,
    val flipX: Boolean,
    val dt: Double,
    val torso: Double,
) {
    val size get() = frames.size
    fun toRawX(x: Double) = if (flipX) width - x else x
    fun t(i: Int) = frames[i].t

    /** Real time at a fractional sample index. */
    fun timeAt(i: Double): Double {
        val lo = i.toInt().coerceIn(0, size - 1)
        val hi = (lo + 1).coerceAtMost(size - 1)
        val f = (i - lo).coerceIn(0.0, 1.0)
        return frames[lo].t + (frames[hi].t - frames[lo].t) * f
    }

    fun p(i: Int, lm: Int) = frames[i].p(lm)
    fun mid(i: Int, a: Int, b: Int) = Vec.mid(frames[i].p(a), frames[i].p(b))
    fun hands(i: Int) = mid(i, Lm.L_WRIST, Lm.R_WRIST)
    fun hips(i: Int) = mid(i, Lm.L_HIP, Lm.R_HIP)
    fun shoulders(i: Int) = mid(i, Lm.L_SHOULDER, Lm.R_SHOULDER)
    fun head(i: Int) = mid(i, Lm.L_EAR, Lm.R_EAR)

    /** Visibility of one landmark, or the mean of a pair used as a midpoint. */
    fun vis(i: Int, vararg lms: Int) = lms.map { frames[i].visibility[it] }.average()
}

data class ClipGuess(val view: CameraView, val handedness: Handedness, val shoulderWidthRatio: Double)

object Normalizer {

    fun normalize(raw: PoseTrack, view: CameraView, handedness: Handedness): NormalizedTrack {
        require(raw.frames.count { it.found } >= 5) { "The golfer was found in too few frames." }
        var frames = fillGaps(raw.frames)
        val w = raw.width
        if (handedness == Handedness.LEFT) frames = frames.map { mirror(it, w, swapLabels = true) }

        // Orient so that +x is toward the ball (DTL) or the target (face-on).
        val early = frames.take(maxOf(3, (0.3 / raw.dt).roundToInt()).coerceAtMost(frames.size))
        val dir = when (view) {
            CameraView.DOWN_THE_LINE -> early.map { f ->
                Vec.mid(f.p(Lm.L_WRIST), f.p(Lm.R_WRIST)).x - Vec.mid(f.p(Lm.L_HIP), f.p(Lm.R_HIP)).x
            }.average()
            CameraView.FACE_ON -> early.map { f -> f.x[Lm.L_SHOULDER] - f.x[Lm.R_SHOULDER] }.average()
        }
        val cameraFlip = dir < 0
        if (cameraFlip) frames = frames.map { mirror(it, w, swapLabels = false) }

        val dt = raw.dt
        frames = smooth(frames, halfWindow = (0.5 * (1.0 / dt) / 60.0).roundToInt())
        val torso = early.indices.map { i ->
            val f = frames[i]
            Vec.mid(f.p(Lm.L_SHOULDER), f.p(Lm.R_SHOULDER)).dist(Vec.mid(f.p(Lm.L_HIP), f.p(Lm.R_HIP)))
        }.average()
        require(torso > 10) { "The golfer is too small in the frame." }
        val totalFlip = (handedness == Handedness.LEFT) xor cameraFlip
        return NormalizedTrack(frames, raw.width, raw.height, totalFlip, dt, torso)
    }

    /**
     * Guesses view and handedness from the first frames, to warn when the user's choice looks wrong.
     * Face-on shows the shoulders side by side; down-the-line shows them nearly overlapping.
     */
    fun guess(raw: PoseTrack): ClipGuess? {
        val early = raw.frames.filter { it.found }.take(10)
        if (early.size < 3) return null
        val ratio = early.map { f ->
            val sw = abs(f.x[Lm.L_SHOULDER] - f.x[Lm.R_SHOULDER])
            val torso = Vec.mid(f.p(Lm.L_SHOULDER), f.p(Lm.R_SHOULDER)).dist(Vec.mid(f.p(Lm.L_HIP), f.p(Lm.R_HIP)))
            sw / torso
        }.average()
        val view = if (ratio > 0.45) CameraView.FACE_ON else CameraView.DOWN_THE_LINE
        val handed = when (view) {
            // From behind a right-hander, the right (trail) side is nearer the camera and better seen.
            CameraView.DOWN_THE_LINE -> {
                val right = early.map { f -> listOf(Lm.R_SHOULDER, Lm.R_ELBOW, Lm.R_WRIST, Lm.R_HIP, Lm.R_KNEE).map { f.visibility[it] }.average() }.average()
                val left = early.map { f -> listOf(Lm.L_SHOULDER, Lm.L_ELBOW, Lm.L_WRIST, Lm.L_HIP, Lm.L_KNEE).map { f.visibility[it] }.average() }.average()
                if (right >= left) Handedness.RIGHT else Handedness.LEFT
            }
            // Facing a right-hander, the left (lead) shoulder appears on the right of the picture.
            CameraView.FACE_ON -> if (early.map { it.x[Lm.L_SHOULDER] - it.x[Lm.R_SHOULDER] }.average() >= 0) Handedness.RIGHT else Handedness.LEFT
        }
        return ClipGuess(view, handed, ratio)
    }

    private fun mirror(f: PoseFrame, width: Int, swapLabels: Boolean): PoseFrame {
        val x = DoubleArray(Lm.COUNT) { width - f.x[it] }
        val y = f.y.copyOf()
        val v = f.visibility.copyOf()
        if (swapLabels) {
            for ((a, b) in Lm.PAIRS) {
                x[a] = width - f.x[b]; x[b] = width - f.x[a]
                y[a] = f.y[b]; y[b] = f.y[a]
                v[a] = f.visibility[b]; v[b] = f.visibility[a]
            }
        }
        return PoseFrame(f.index, f.t, x, y, v, f.found)
    }

    /** Linear interpolation over frames where the golfer wasn't found; ends hold the nearest value. */
    private fun fillGaps(frames: List<PoseFrame>): List<PoseFrame> {
        val good = frames.indices.filter { frames[it].found }
        if (good.size == frames.size) return frames
        return frames.indices.map { i ->
            val f = frames[i]
            if (f.found) return@map f
            val prev = good.lastOrNull { it < i }
            val next = good.firstOrNull { it > i }
            val x = DoubleArray(Lm.COUNT); val y = DoubleArray(Lm.COUNT)
            for (k in 0 until Lm.COUNT) {
                when {
                    prev != null && next != null -> {
                        val a = (i - prev).toDouble() / (next - prev)
                        x[k] = frames[prev].x[k] + (frames[next].x[k] - frames[prev].x[k]) * a
                        y[k] = frames[prev].y[k] + (frames[next].y[k] - frames[prev].y[k]) * a
                    }
                    prev != null -> { x[k] = frames[prev].x[k]; y[k] = frames[prev].y[k] }
                    else -> { x[k] = frames[next!!].x[k]; y[k] = frames[next].y[k] }
                }
            }
            PoseFrame(f.index, f.t, x, y, DoubleArray(Lm.COUNT), false)
        }
    }

    /** Centered moving average; [halfWindow] = 0 leaves the data unchanged (30–60 fps). */
    private fun smooth(frames: List<PoseFrame>, halfWindow: Int): List<PoseFrame> {
        if (halfWindow <= 0) return frames
        return frames.indices.map { i ->
            val lo = maxOf(0, i - halfWindow); val hi = minOf(frames.size - 1, i + halfWindow)
            val n = hi - lo + 1
            val x = DoubleArray(Lm.COUNT); val y = DoubleArray(Lm.COUNT)
            for (j in lo..hi) for (k in 0 until Lm.COUNT) { x[k] += frames[j].x[k] / n; y[k] += frames[j].y[k] / n }
            PoseFrame(frames[i].index, frames[i].t, x, y, frames[i].visibility, frames[i].found)
        }
    }
}
