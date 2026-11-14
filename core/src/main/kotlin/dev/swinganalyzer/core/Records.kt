package dev.swinganalyzer.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything about one analysed swing that the app keeps: no video, only numbers and key frames. */
class AnalysisResult(
    val id: String,
    val createdAt: Long,
    val clubId: String,
    val clubName: String,
    val shot: ShotType,
    val view: CameraView,
    val handedness: Handedness,
    val heightCm: Double,
    val videoFps: Double,
    val slowMotion: Double,
    val feedback: Feedback,
    val tempo: TempoInfo?,
    val keyFrames: List<KeyFrame>,
    val eventTimes: Map<String, Double>,
    val warnings: List<String>,
    val shaftReadings: Map<Int, Double>,
    /** Raw landmarks, kept so swings can be re-scored when the rules change. */
    val track: PoseTrack,
    val knowledgeVersion: String,
    /** Rule results and raw metrics from this run; not saved (re-derived when re-scoring). */
    val findings: List<Finding> = emptyList(),
    val metrics: Map<String, Measured> = emptyMap(),
) {
    fun clipText(): String {
        val speed = if (slowMotion > 1.01) "slow motion ×%.0f".format(Locale.US, slowMotion) else "normal speed"
        return "%.0f fps, %s".format(Locale.US, videoFps, speed)
    }

    fun toJson(): String = Json.write(mapOf(
        "id" to id, "createdAt" to createdAt, "clubId" to clubId, "clubName" to clubName,
        "shot" to shot.id, "view" to view.id, "handedness" to handedness.id, "heightCm" to heightCm,
        "videoFps" to videoFps, "slowMotion" to slowMotion, "feedback" to feedbackMap(feedback),
        "tempo" to tempo?.let { mapOf("back" to it.back, "down" to it.down, "ratio" to it.ratio, "fromClub" to it.fromClub,
            "rangeLow" to it.rangeLow, "rangeHigh" to it.rangeHigh) },
        "keyFrames" to keyFrames.map { mapOf("label" to it.label, "frame" to it.frameIndex) },
        "eventTimes" to eventTimes, "warnings" to warnings,
        "shaftReadings" to shaftReadings.mapKeys { it.key.toString() },
        "track" to trackMap(track), "knowledgeVersion" to knowledgeVersion,
    ), indent = false)

    companion object {
        fun fromJson(text: String): AnalysisResult {
            val o = Json.parse(text).obj()
            val tempo = o["tempo"]?.obj()?.let {
                TempoInfo(it.numOrNull("back"), it.numOrNull("down"), it.numOrNull("ratio"), it.bool("fromClub"),
                    it.numOrNull("rangeLow"), it.numOrNull("rangeHigh"))
            }
            return AnalysisResult(
                id = o.str("id"), createdAt = o.num("createdAt").toLong(), clubId = o.str("clubId"),
                clubName = o.str("clubName"), shot = ShotType.fromId(o.str("shot")), view = CameraView.fromId(o.str("view")),
                handedness = Handedness.fromId(o.str("handedness")), heightCm = o.num("heightCm"),
                videoFps = o.num("videoFps"), slowMotion = o.num("slowMotion"), feedback = feedbackFrom(o["feedback"].obj()),
                tempo = tempo,
                keyFrames = o["keyFrames"].arr().map { val k = it.obj(); KeyFrame(k.str("label"), k.num("frame").toInt()) },
                eventTimes = o["eventTimes"].obj().mapValues { (it.value as Number).toDouble() },
                warnings = o.strList("warnings"),
                shaftReadings = o["shaftReadings"].obj().entries.associate { it.key.toInt() to (it.value as Number).toDouble() },
                track = trackFrom(o["track"].obj()), knowledgeVersion = o.str("knowledgeVersion"),
            )
        }

        private fun feedbackMap(f: Feedback) = mapOf(
            "summary" to f.summary, "working" to f.working,
            "workOn" to f.workOn.map {
                mapOf("ruleId" to it.ruleId, "faultId" to it.faultId, "title" to it.title, "seen" to it.seen, "why" to it.why,
                    "drillName" to it.drillName, "drillSteps" to it.drillSteps, "severe" to it.severe)
            },
            "tempo" to f.tempo, "couldntJudge" to f.couldntJudge,
            "numbers" to f.numbers.map { mapOf("name" to it.name, "value" to it.value, "expected" to it.expected, "status" to it.status.name, "unusual" to it.unusual) },
            "bodyNote" to f.bodyNote,
        )

        private fun feedbackFrom(o: Map<String, Any?>) = Feedback(
            summary = o.str("summary"), working = o.strList("working"),
            workOn = o["workOn"].arr().map {
                val w = it.obj()
                WorkOnItem(w.str("ruleId"), w.str("faultId"), w.str("title"), w.str("seen"), w.str("why"),
                    w.strOrNull("drillName"), w.strList("drillSteps"), w.bool("severe"))
            },
            tempo = o.strOrNull("tempo"), couldntJudge = o.strList("couldntJudge"),
            numbers = o["numbers"].arr().map {
                val r = it.obj()
                NumberRow(r.str("name"), r.str("value"), r.str("expected"), Status.valueOf(r.str("status")), r.bool("unusual"))
            },
            bodyNote = o.strOrNull("bodyNote"),
        )

        private fun r1(v: Double) = if (v.isNaN()) null else Math.round(v * 10) / 10.0
        private fun r2(v: Double) = Math.round(v * 100) / 100.0

        private fun trackMap(t: PoseTrack) = mapOf(
            "w" to t.width, "h" to t.height,
            "frames" to t.frames.map { f ->
                mapOf("i" to f.index, "t" to Math.round(f.t * 10000) / 10000.0, "found" to f.found,
                    "x" to f.x.map { r1(it) }, "y" to f.y.map { r1(it) }, "v" to f.visibility.map { r2(it) })
            },
        )

        fun trackFrom(o: Map<String, Any?>): PoseTrack {
            val frames = o["frames"].arr().map {
                val f = it.obj()
                fun arr(key: String) = f[key].arr().map { v -> (v as Number?)?.toDouble() ?: Double.NaN }.toDoubleArray()
                PoseFrame(f.num("i").toInt(), f.num("t"), arr("x"), arr("y"), arr("v"), f.bool("found"))
            }
            return PoseTrack(frames, o.num("w").toInt(), o.num("h").toInt())
        }
    }
}

/** A saved note: the feedback as it was shown, plus the club and the user's own comment. */
data class Note(
    val id: String,
    val createdAt: Long,
    val analysisId: String?,
    val clubId: String,
    val clubName: String,
    val shot: ShotType,
    val view: CameraView,
    val clip: String,
    val feedbackText: String,
    val tempoText: String,
    val numbersText: String,
    val couldntJudgeText: String,
    val comment: String,
    val isReference: Boolean,
) {
    fun dateText(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(createdAt))

    /** Plain-text export, laid out as in swing-analyzer-architecture.md §13. */
    fun exportText(): String = buildString {
        appendLine("SWING NOTE")
        appendLine("Date:    ${dateText()}")
        appendLine("Club:    $clubName")
        appendLine("Shot:    ${shot.label}")
        appendLine("View:    ${view.label}")
        appendLine("Clip:    $clip")
        if (isReference) appendLine("Marked as reference swing for this club")
        appendLine()
        appendLine("FEEDBACK")
        appendLine(feedbackText)
        appendLine()
        appendLine("TEMPO")
        appendLine(tempoText.ifBlank { "Not measured." })
        appendLine()
        appendLine("KEY NUMBERS")
        appendLine(numbersText.ifBlank { "None." })
        appendLine()
        appendLine("COULD NOT JUDGE")
        appendLine(couldntJudgeText.ifBlank { "Nothing." })
        appendLine()
        appendLine("MY COMMENT")
        appendLine(comment.ifBlank { "—" })
    }

    fun fileName(): String {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.US).withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(createdAt))
        val club = clubName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val v = if (view == CameraView.DOWN_THE_LINE) "dtl" else "face-on"
        return "swing-note_${date}_${club}_$v.txt"
    }

    fun toJson(): String = Json.write(mapOf(
        "id" to id, "createdAt" to createdAt, "analysisId" to analysisId, "clubId" to clubId, "clubName" to clubName,
        "shot" to shot.id, "view" to view.id, "clip" to clip, "feedbackText" to feedbackText, "tempoText" to tempoText,
        "numbersText" to numbersText, "couldntJudgeText" to couldntJudgeText, "comment" to comment, "isReference" to isReference,
    ))

    companion object {
        fun fromAnalysis(id: String, createdAt: Long, a: AnalysisResult, comment: String, isReference: Boolean) = Note(
            id, createdAt, a.id, a.clubId, a.clubName, a.shot, a.view, a.clipText(), a.feedback.feedbackText(),
            a.feedback.tempo ?: "", a.feedback.numbersText(), a.feedback.couldntJudgeText(), comment, isReference,
        )

        fun fromJson(text: String): Note {
            val o = Json.parse(text).obj()
            return Note(o.str("id"), o.num("createdAt").toLong(), o.strOrNull("analysisId"), o.str("clubId"), o.str("clubName"),
                ShotType.fromId(o.str("shot")), CameraView.fromId(o.str("view")), o.str("clip"), o.str("feedbackText"),
                o.str("tempoText"), o.str("numbersText"), o.str("couldntJudgeText"), o.str("comment"), o.bool("isReference"))
        }

        /** Several notes in one file, oldest first, separated by a divider. */
        fun exportAll(notes: List<Note>): String =
            notes.sortedBy { it.createdAt }.joinToString("\n\n" + "=".repeat(60) + "\n\n") { it.exportText() }
    }
}
