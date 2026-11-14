package dev.swinganalyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import dev.swinganalyzer.core.AnalysisRequest
import dev.swinganalyzer.core.AnalysisResult
import dev.swinganalyzer.core.CameraView
import dev.swinganalyzer.core.GrayCrop
import dev.swinganalyzer.core.Handedness
import dev.swinganalyzer.core.Knowledge
import dev.swinganalyzer.core.Lm
import dev.swinganalyzer.core.PoseFrame
import dev.swinganalyzer.core.PoseTrack
import dev.swinganalyzer.core.ShotType
import dev.swinganalyzer.core.SwingAnalyzer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the app learns from the file before analysing it. */
data class ClipInfo(
    val durationS: Double,
    val frameCount: Int,
    val fps: Double,
    val width: Int,
    val height: Int,
    val rotation: Int,
    /** Capture frame rate recorded by the camera, if any (slow-motion clips). */
    val captureFps: Double?,
) {
    /** Suggested slow-motion factor from the capture rate: 1, 2, 4 or 8. */
    val suggestedSlowMotion: Double
        get() {
            val c = captureFps ?: return 1.0
            if (c < fps * 1.5) return 1.0
            return listOf(2.0, 4.0, 8.0).minBy { kotlin.math.abs(it - c / fps) }
        }
}

class AnalysisCancelled : Exception("Cancelled")

/**
 * The on-device pipeline: decode frames → MediaPipe Pose Landmarker (VIDEO mode) → core analysis →
 * shaft crops → key-frame images. Nothing is sent anywhere; only results and key frames are written.
 */
class VideoAnalyzer(private val context: Context) {

    companion object {
        const val MODEL = "pose_landmarker_heavy.task"
        private const val MAX_SIDE = 1280
        private const val POSE_FPS_CAP = 120.0
    }

    fun readInfo(uri: Uri): ClipInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            fun meta(key: Int) = r.extractMetadata(key)
            val durationS = (meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0) / 1000.0
            val frames = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            require(durationS > 0 && frames > 0) { "This file doesn't look like a video the phone can read." }
            return ClipInfo(
                durationS = durationS,
                frameCount = frames,
                fps = frames / durationS,
                width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                captureFps = meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull(),
            )
        } finally {
            r.release()
        }
    }

    class Options(
        val clubId: String,
        val shot: ShotType,
        val view: CameraView,
        val handedness: Handedness,
        val slowMotion: Double,
        val heightCm: Double,
    )

    /**
     * Runs the whole analysis and writes key frames into [outDir]. [progress] gets a step name and 0–1.
     * Must run off the main thread, on one thread throughout (the GPU delegate is thread-bound).
     */
    fun analyze(
        uri: Uri,
        info: ClipInfo,
        opts: Options,
        knowledge: Knowledge,
        id: String,
        outDir: File,
        progress: (String, Float) -> Unit,
        isCancelled: () -> Boolean,
    ): AnalysisResult {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        try {
            val realFps = info.fps * opts.slowMotion
            val decoder = FrameDecoder(retriever, info)

            // 1. Find the swing: a quick pass at about 15 samples per real second, if the clip is long.
            progress("Finding the swing", 0.02f)
            val window: IntRange = if (info.frameCount <= 240 && info.durationS / opts.slowMotion <= 8.0) {
                0 until info.frameCount
            } else {
                findSwingWindow(decoder, info, realFps, isCancelled) { f -> progress("Finding the swing", 0.02f + 0.13f * f) }
            }

            // 2. Track the body on every frame of the window (thinned to ~120 real fps for slow motion).
            val stride = max(1, (realFps / POSE_FPS_CAP).toInt())
            val indices = window.step(stride).toList()
            val frames = ArrayList<PoseFrame>(indices.size)
            createLandmarker().use { lm ->
                var done = 0
                // Decode consecutive frames in batches (much faster than seeking one at a time).
                var pos = window.first
                while (pos <= window.last) {
                    if (isCancelled()) throw AnalysisCancelled()
                    val count = min(decoder.batchSize, window.last - pos + 1)
                    val batch = decoder.frames(pos, count)
                    for ((j, bmp) in batch.withIndex()) {
                        val i = pos + j
                        if ((i - window.first) % stride == 0) {
                            frames.add(lm.detect(bmp, i, info.fps, opts.slowMotion))
                            done++
                        }
                        bmp.recycle()
                    }
                    pos += count
                    progress("Tracking your body ($done of ${indices.size})", 0.15f + 0.65f * done / indices.size)
                }
            }
            val track = PoseTrack(frames, decoder.width, decoder.height)

            // 3. Key positions, then the shaft at the top from grayscale crops.
            progress("Finding the club at the top", 0.82f)
            val request = AnalysisRequest(track, opts.view, opts.handedness, opts.shot, opts.clubId, opts.heightCm,
                info.fps, opts.slowMotion)
            val prepared = SwingAnalyzer.prepare(request)
            val crops = prepared.cropRequests.mapNotNull { r ->
                if (isCancelled()) throw AnalysisCancelled()
                decoder.frame(r.frameIndex)?.let { bmp -> grayCrop(bmp, r.frameIndex, r.x0, r.y0, r.w, r.h).also { bmp.recycle() } }
            }

            progress("Measuring and writing feedback", 0.9f)
            val result = SwingAnalyzer.finish(prepared, crops, knowledge, id, System.currentTimeMillis())

            // 4. Key-frame images with the skeleton drawn on: the only pictures the app keeps.
            progress("Saving key frames", 0.95f)
            outDir.mkdirs()
            val byIndex = track.frames.associateBy { it.index }
            for (k in result.keyFrames) {
                val bmp = decoder.frame(k.frameIndex) ?: continue
                byIndex[k.frameIndex]?.let { drawSkeleton(bmp, it) }
                saveJpeg(scaleTo(bmp, 540), File(outDir, keyFrameFile(k.label)))
                bmp.recycle()
            }
            return result
        } finally {
            retriever.release()
        }
    }

    private fun findSwingWindow(
        decoder: FrameDecoder,
        info: ClipInfo,
        realFps: Double,
        isCancelled: () -> Boolean,
        progress: (Float) -> Unit,
    ): IntRange {
        val step = max(1, (realFps / 15.0).roundToInt())
        val samples = (0 until info.frameCount step step).toList()
        val hands = ArrayList<Pair<Int, Pair<Double, Double>>>()
        createLandmarker().use { lm ->
            for ((n, i) in samples.withIndex()) {
                if (isCancelled()) throw AnalysisCancelled()
                val bmp = decoder.frame(i) ?: continue
                val f = lm.detect(bmp, i, info.fps, 1.0)
                bmp.recycle()
                if (f.found) hands.add(i to Pair((f.x[Lm.L_WRIST] + f.x[Lm.R_WRIST]) / 2, (f.y[Lm.L_WRIST] + f.y[Lm.R_WRIST]) / 2))
                progress(n.toFloat() / samples.size)
            }
        }
        if (hands.size < 3) return 0 until info.frameCount
        // The fastest hand movement is close to impact.
        val peak = hands.zipWithNext().maxBy { (a, b) ->
            val dx = b.second.first - a.second.first; val dy = b.second.second - a.second.second
            dx * dx + dy * dy
        }.second.first
        val before = (2.5 * realFps).roundToInt()
        val after = (1.5 * realFps).roundToInt()
        return max(0, peak - before)..min(info.frameCount - 1, peak + after)
    }

    // --- MediaPipe ---

    /** Wraps a PoseLandmarker; if the GPU delegate fails mid-run, it rebuilds on the CPU and carries on. */
    private class Landmarker(private var lm: PoseLandmarker, private val cpuFallback: (() -> PoseLandmarker)?) : AutoCloseable {
        private var lastTs = -1L
        private var onCpu = cpuFallback == null

        fun detect(bmp: Bitmap, frameIndex: Int, fps: Double, slowMotion: Double): PoseFrame {
            // Timestamps in video time (strictly increasing, as VIDEO mode requires).
            var ts = (frameIndex * 1000.0 / fps).toLong()
            if (ts <= lastTs) ts = lastTs + 1
            lastTs = ts
            val image = BitmapImageBuilder(bmp).build()
            val result = try {
                lm.detectForVideo(image, ts)
            } catch (e: RuntimeException) {
                if (onCpu || cpuFallback == null) throw e
                lm.close()
                lm = cpuFallback.invoke()
                onCpu = true
                lm.detectForVideo(image, ts)
            }
            val t = frameIndex / fps / slowMotion
            val poses = result.landmarks()
            if (poses.isEmpty()) return PoseFrame.missing(frameIndex, t)
            val p = poses[0]
            val x = DoubleArray(Lm.COUNT); val y = DoubleArray(Lm.COUNT); val v = DoubleArray(Lm.COUNT)
            for (k in 0 until min(Lm.COUNT, p.size)) {
                x[k] = p[k].x().toDouble() * bmp.width
                y[k] = p[k].y().toDouble() * bmp.height
                v[k] = p[k].visibility().orElse(0f).toDouble()
            }
            return PoseFrame(frameIndex, t, x, y, v, true)
        }

        override fun close() = lm.close()
    }

    private fun createLandmarker(): Landmarker {
        fun build(delegate: Delegate): PoseLandmarker {
            val base = BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(delegate).build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            return PoseLandmarker.createFromOptions(context, options)
        }
        return try {
            Landmarker(build(Delegate.GPU)) { build(Delegate.CPU) }
        } catch (e: Throwable) {
            Landmarker(build(Delegate.CPU), null)
        }
    }

    // --- Frames ---

    /** Decodes frames upright and scaled so the long side is at most 1280 px. */
    private class FrameDecoder(private val r: MediaMetadataRetriever, private val info: ClipInfo) {
        private val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
        private val rotated = info.rotation == 90 || info.rotation == 270
        private val uprightW = if (rotated) info.height else info.width
        private val uprightH = if (rotated) info.width else info.height
        private val scale = min(1.0, MAX_SIDE.toDouble() / max(uprightW, uprightH))
        val width = (uprightW * scale).roundToInt()
        val height = (uprightH * scale).roundToInt()

        /** Frames per batch, keeping a batch of raw frames under about 64 MB. */
        val batchSize = (64L * 1024 * 1024 / (max(1, info.width) * max(1, info.height) * 4L)).toInt().coerceIn(1, 16)

        fun frame(i: Int): Bitmap? {
            val raw = try {
                r.getFrameAtIndex(i.coerceIn(0, info.frameCount - 1), params)
            } catch (e: Exception) {
                null
            } ?: return null
            return prepare(raw)
        }

        fun frames(start: Int, count: Int): List<Bitmap> {
            val raws = try {
                r.getFramesAtIndex(start, count, params)
            } catch (e: Exception) {
                // Some files refuse batch decoding; fall back to one frame at a time.
                return (start until start + count).mapNotNull { frame(it) }
            }
            return raws.map { prepare(it) }
        }

        private fun prepare(raw: Bitmap): Bitmap {
            // Some devices return frames already rotated; rotate only if the shape says otherwise.
            val needsRotation = info.rotation != 0 && (raw.width > raw.height) != (uprightW > uprightH)
            val upright = if (needsRotation) {
                val m = Matrix().apply { postRotate(info.rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
            } else raw
            if (upright.width == width && upright.height == height) return upright
            return Bitmap.createScaledBitmap(upright, width, height, true).also { if (it !== upright) upright.recycle() }
        }
    }

    private fun grayCrop(bmp: Bitmap, frameIndex: Int, x0: Int, y0: Int, w: Int, h: Int): GrayCrop {
        val cx = x0.coerceIn(0, bmp.width - 1); val cy = y0.coerceIn(0, bmp.height - 1)
        val cw = min(w, bmp.width - cx); val ch = min(h, bmp.height - cy)
        val argb = IntArray(cw * ch)
        bmp.getPixels(argb, 0, cw, cx, cy, cw, ch)
        val gray = ByteArray(cw * ch) { i ->
            val c = argb[i]
            val lum = 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
            lum.roundToInt().coerceIn(0, 255).toByte()
        }
        return GrayCrop(frameIndex, cx, cy, cw, ch, gray)
    }

    private fun drawSkeleton(bmp: Bitmap, f: PoseFrame) {
        if (!f.found) return
        val c = Canvas(bmp)
        val s = bmp.width / 540f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 230, 120); strokeWidth = 4f * s; style = Paint.Style.STROKE }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        for ((a, b) in Lm.EDGES) {
            if (f.x[a].isNaN() || f.x[b].isNaN()) continue
            c.drawLine(f.x[a].toFloat(), f.y[a].toFloat(), f.x[b].toFloat(), f.y[b].toFloat(), line)
        }
        for (k in listOf(Lm.L_SHOULDER, Lm.R_SHOULDER, Lm.L_HIP, Lm.R_HIP, Lm.L_WRIST, Lm.R_WRIST, Lm.L_KNEE, Lm.R_KNEE)) {
            if (!f.x[k].isNaN()) c.drawCircle(f.x[k].toFloat(), f.y[k].toFloat(), 5f * s, dot)
        }
    }

    private fun scaleTo(bmp: Bitmap, w: Int): Bitmap =
        if (bmp.width <= w) bmp else Bitmap.createScaledBitmap(bmp, w, (bmp.height * w.toDouble() / bmp.width).roundToInt(), true)

    private fun saveJpeg(bmp: Bitmap, file: File) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
}

fun keyFrameFile(label: String) = label.lowercase().replace(Regex("[^a-z0-9]+"), "_") + ".jpg"
