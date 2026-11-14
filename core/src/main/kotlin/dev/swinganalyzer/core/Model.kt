package dev.swinganalyzer.core

import kotlin.math.hypot

enum class CameraView(val id: String, val label: String) {
    DOWN_THE_LINE("dtl", "Down-the-line"),
    FACE_ON("fo", "Face-on");

    companion object {
        fun fromId(id: String) = entries.first { it.id == id }
    }
}

enum class Handedness(val id: String, val label: String) {
    RIGHT("right", "Right-handed"),
    LEFT("left", "Left-handed");

    companion object {
        fun fromId(id: String) = entries.first { it.id == id }
    }
}

enum class ShotType(val id: String, val label: String) {
    FULL("full", "Full swing"),
    CHIP("chip", "Chip or pitch"),
    PUTT("putt", "Putt");

    companion object {
        fun fromId(id: String) = entries.first { it.id == id }
    }
}

/** MediaPipe Pose Landmarker indices (33-point BlazePose topology). */
object Lm {
    const val NOSE = 0
    const val L_EAR = 7
    const val R_EAR = 8
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_ELBOW = 13
    const val R_ELBOW = 14
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_KNEE = 25
    const val R_KNEE = 26
    const val L_ANKLE = 27
    const val R_ANKLE = 28
    const val L_HEEL = 29
    const val R_HEEL = 30
    const val L_FOOT = 31
    const val R_FOOT = 32
    const val COUNT = 33

    /** Left/right pairs, swapped when mirroring a left-handed golfer. */
    val PAIRS = listOf(
        1 to 4, 2 to 5, 3 to 6, 7 to 8, 9 to 10, 11 to 12, 13 to 14, 15 to 16, 17 to 18,
        19 to 20, 21 to 22, 23 to 24, 25 to 26, 27 to 28, 29 to 30, 31 to 32,
    )

    /** Skeleton edges for drawing. */
    val EDGES = listOf(
        L_SHOULDER to R_SHOULDER, L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST, R_SHOULDER to R_ELBOW,
        R_ELBOW to R_WRIST, L_SHOULDER to L_HIP, R_SHOULDER to R_HIP, L_HIP to R_HIP, L_HIP to L_KNEE,
        L_KNEE to L_ANKLE, R_HIP to R_KNEE, R_KNEE to R_ANKLE, L_ANKLE to L_HEEL, R_ANKLE to R_HEEL,
        L_HEEL to L_FOOT, R_HEEL to R_FOOT, L_ANKLE to L_FOOT, R_ANKLE to R_FOOT,
    )
}

data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec(x * k, y * k)
    fun len() = hypot(x, y)
    fun dist(o: Vec) = hypot(x - o.x, y - o.y)
    companion object {
        fun mid(a: Vec, b: Vec) = Vec((a.x + b.x) / 2, (a.y + b.y) / 2)
    }
}

/**
 * Landmarks for one video frame, in pixels of the (upright) frame.
 * [t] is real time in seconds, already corrected for slow motion.
 */
class PoseFrame(
    val index: Int,
    val t: Double,
    val x: DoubleArray,
    val y: DoubleArray,
    val visibility: DoubleArray,
    val found: Boolean,
) {
    fun p(i: Int) = Vec(x[i], y[i])

    companion object {
        fun missing(index: Int, t: Double) = PoseFrame(
            index, t, DoubleArray(Lm.COUNT) { Double.NaN }, DoubleArray(Lm.COUNT) { Double.NaN },
            DoubleArray(Lm.COUNT), false,
        )
    }
}

/** All tracked frames of one clip plus the frame size. */
class PoseTrack(val frames: List<PoseFrame>, val width: Int, val height: Int) {
    val size get() = frames.size

    /** Median sample interval in real seconds. */
    val dt: Double
        get() {
            if (frames.size < 2) return 1.0 / 30
            val d = frames.zipWithNext { a, b -> b.t - a.t }.filter { it > 0 }.sorted()
            return if (d.isEmpty()) 1.0 / 30 else d[d.size / 2]
        }
}
