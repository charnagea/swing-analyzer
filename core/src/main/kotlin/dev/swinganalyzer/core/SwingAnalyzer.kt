package dev.swinganalyzer.core

import java.util.Locale
import kotlin.math.roundToInt

class AnalysisRequest(
    val track: PoseTrack,
    val view: CameraView,
    val handedness: Handedness,
    val shot: ShotType,
    val clubId: String,
    val heightCm: Double,
    /** Frame rate of the video file as played back. */
    val videoFps: Double,
    /** 1 for normal speed; 4 or 8 for slow motion (capture rate ÷ playback rate). */
    val slowMotion: Double,
)

/** The result of the first half of the analysis: key positions found, crops still needed for the shaft. */
class Prepared internal constructor(
    val request: AnalysisRequest,
    val track: NormalizedTrack,
    val events: SwingEvents,
    val cropRequests: List<CropRequest>,
    val guess: ClipGuess?,
)

data class KeyFrame(val label: String, val frameIndex: Int)

/**
 * Runs the analysis in two halves so the Android layer can decode the extra frame crops the shaft
 * finder needs in between: [prepare] → decode [Prepared.cropRequests] → [finish].
 */
object SwingAnalyzer {

    fun prepare(request: AnalysisRequest): Prepared {
        val guess = Normalizer.guess(request.track)
        val norm = Normalizer.normalize(request.track, request.view, request.handedness)
        val events = EventFinder.find(norm, request.shot)
        return Prepared(request, norm, events, ShaftFinder.cropRequests(norm, events), guess)
    }

    fun finish(
        prepared: Prepared,
        crops: List<GrayCrop>,
        knowledge: Knowledge,
        id: String,
        createdAt: Long,
    ): AnalysisResult {
        val req = prepared.request
        val n = prepared.track
        val club = knowledge.club(req.clubId)
        val sampleOf = n.frames.withIndex().associate { it.value.index to it.index }

        val readings = crops.mapNotNull { c ->
            val s = sampleOf[c.frameIndex] ?: return@mapNotNull null
            ShaftFinder.read(n, s, c)?.let { s to it.angleDeg }
        }.toMap()
        val turn = ShaftFinder.clubTurn(n, prepared.events, readings)
        val ev = if (turn != null) prepared.events.copy(clubTop = turn.first, clubDownSub = turn.second) else prepared.events

        val metrics = Metrics.compute(n, ev, req.view)
        val findings = RuleEngine.evaluate(knowledge, metrics, club, req.shot, req.view)
        val tempo = tempoInfo(n, ev)
        val feedback = Coach.build(knowledge, findings, club, req.shot, req.view, tempo, req.heightCm)

        val warnings = buildList {
            prepared.guess?.let { g ->
                if (g.view != req.view) add("This clip looks like a ${g.view.label.lowercase(Locale.US)} view, but it was analysed as ${req.view.label.lowercase(Locale.US)}. Re-run with the other view if that's wrong.")
                if (g.handedness != req.handedness) add("The golfer looks ${g.handedness.label.lowercase(Locale.US)}. Re-run with that setting if so.")
            }
            val realFps = 1.0 / n.dt
            if (realFps < 50) add("Filmed at %.0f frames per second: tempo and impact are approximate. 120 fps or slow motion gives sharper results.".format(Locale.US, realFps))
            if (req.shot == ShotType.FULL && ev.clubDownSub == null) add("The club wasn't found at the top of the swing (a busy background can hide it), so the top comes from your hands and tempo is shown as a range.")
            if (ev.finish == null) add("The clip ends before the finish settles, so finish checks are limited.")
        }

        fun raw(sample: Int) = n.frames[sample.coerceIn(0, n.size - 1)].index
        val keyFrames = if (req.shot == ShotType.FULL) listOfNotNull(
            KeyFrame("Address", raw(ev.address)),
            KeyFrame("Top", raw(ev.top)),
            KeyFrame("Impact", raw(ev.impactSub.roundToInt())),
            ev.finish?.let { KeyFrame("Finish", raw(minOf(n.size - 1, it + (0.3 / n.dt).roundToInt()))) },
        ) else listOfNotNull(
            KeyFrame("Address", raw(ev.address)),
            KeyFrame("End of backstroke", raw(ev.top)),
            KeyFrame("Impact", raw(ev.impactSub.roundToInt())),
            ev.finish?.let { KeyFrame("Finish", raw(it)) },
        )
        val eventTimes = buildMap {
            put("start", n.timeAt(ev.startSub))
            put("top", n.timeAt(ev.top.toDouble()))
            ev.clubDownSub?.let { put("clubStartsDown", n.timeAt(it)) }
            put("impact", n.timeAt(ev.impactSub))
            ev.finish?.let { put("finish", n.timeAt(it.toDouble())) }
        }
        return AnalysisResult(
            id = id, createdAt = createdAt, clubId = club.id, clubName = club.name, shot = req.shot,
            view = req.view, handedness = req.handedness, heightCm = req.heightCm, videoFps = req.videoFps,
            slowMotion = req.slowMotion, feedback = feedback, tempo = tempo, keyFrames = keyFrames,
            eventTimes = eventTimes, warnings = warnings,
            shaftReadings = readings.mapKeys { n.frames[it.key].index }, track = req.track,
            knowledgeVersion = knowledge.version, findings = findings, metrics = metrics,
        )
    }

    private fun tempoInfo(n: NormalizedTrack, ev: SwingEvents): TempoInfo? {
        val start = n.timeAt(ev.startSub)
        val impact = n.timeAt(ev.impactSub)
        if (ev.shot != ShotType.FULL) {
            val top = n.timeAt(ev.top.toDouble())
            val back = top - start; val down = impact - top
            return if (back > 0 && down > 0) TempoInfo(back, down, back / down, fromClub = false) else null
        }
        ev.clubDownSub?.let { cd ->
            val top = n.timeAt(cd)
            val back = top - start; val down = impact - top
            if (back > 0 && down > 0) return TempoInfo(back, down, back / down, fromClub = true)
        }
        // Fallback: the top lies somewhere between the hands' highest point and the moment they start down.
        val candidates = listOfNotNull(ev.handsHighest, ev.handsStartDown).map { n.timeAt(it.toDouble()) }
            .filter { it > start && it < impact }
            .map { (it - start) / (impact - it) }
        if (candidates.isEmpty()) return null
        return TempoInfo(null, null, null, fromClub = false, candidates.min(), candidates.max())
    }
}
