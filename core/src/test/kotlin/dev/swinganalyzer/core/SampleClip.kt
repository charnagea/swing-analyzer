package dev.swinganalyzer.core

import kotlin.math.abs

/**
 * Acceptance checks on the sample swing (swing-analyzer-architecture.md §14): a 30 fps, down-the-line,
 * right-handed iron swing. Landmarks come from the same MediaPipe heavy model the app bundles
 * (tools/make_fixtures.py). Shared by the JUnit test and a plain main() runner.
 */
object SampleClip {

    data class Check(val name: String, val ok: Boolean, val detail: String)

    private fun resource(path: String): ByteArray =
        SampleClip::class.java.getResourceAsStream(path)?.use { it.readBytes() } ?: error("Missing fixture $path")

    fun loadTrack(): PoseTrack = AnalysisResult.trackFrom(Json.parse(String(resource("/sample/landmarks.json"))).obj())

    fun loadCrops(): List<GrayCrop> = Json.parse(String(resource("/sample/crops.json"))).arr().map { e ->
        val o = e.obj()
        val (w, h, px) = readPgm(resource("/sample/crops/" + o.str("file")))
        require(w == o.num("w").toInt() && h == o.num("h").toInt())
        GrayCrop(o.num("frame").toInt(), o.num("x0").toInt(), o.num("y0").toInt(), w, h, px)
    }

    /** Reads a binary PGM (P5) file. */
    fun readPgm(b: ByteArray): Triple<Int, Int, ByteArray> {
        var i = 0
        fun token(): String {
            while (true) {
                while (b[i].toInt().toChar().isWhitespace()) i++
                if (b[i].toInt().toChar() == '#') { while (b[i].toInt().toChar() != '\n') i++ } else break
            }
            val s = i
            while (!b[i].toInt().toChar().isWhitespace()) i++
            return String(b, s, i - s)
        }
        require(token() == "P5")
        val w = token().toInt(); val h = token().toInt(); token()
        i++ // single whitespace before pixel data
        return Triple(w, h, b.copyOfRange(i, i + w * h))
    }

    private fun request(track: PoseTrack, handedness: Handedness) = AnalysisRequest(track, CameraView.DOWN_THE_LINE,
        handedness, ShotType.FULL, "7i", heightCm = 177.0, videoFps = 30.0, slowMotion = 1.0)

    fun analyze(knowledge: Knowledge = Knowledge.loadBundled()): Pair<Prepared, AnalysisResult> =
        analyzeFull(loadTrack(), Handedness.RIGHT, knowledge, mirrorCrops = false)

    private fun analyzeTrack(track: PoseTrack, handedness: Handedness, k: Knowledge) =
        analyzeFull(track, handedness, k, mirrorCrops = handedness == Handedness.LEFT).second

    private fun analyzeFull(track: PoseTrack, handedness: Handedness, knowledge: Knowledge, mirrorCrops: Boolean): Pair<Prepared, AnalysisResult> {
        val prepared = SwingAnalyzer.prepare(request(track, handedness))
        val crops = loadCrops().map { if (mirrorCrops) mirrorCrop(it, track.width) else it }.associateBy { it.frameIndex }
        // Hand the finder only the crops the core asked for, cut to the requested rectangles.
        val requested = prepared.cropRequests.mapNotNull { r -> crops[r.frameIndex]?.let { cut(it, r) } }
        return prepared to SwingAnalyzer.finish(prepared, requested, knowledge, "sample", 0L)
    }

    /** The clip as a left-hander would look: picture flipped, left/right body labels swapped. */
    fun mirrored(t: PoseTrack): PoseTrack = PoseTrack(t.frames.map { f ->
        val x = DoubleArray(Lm.COUNT) { t.width - f.x[it] }; val y = f.y.copyOf(); val v = f.visibility.copyOf()
        for ((a, b) in Lm.PAIRS) {
            x[a] = t.width - f.x[b]; x[b] = t.width - f.x[a]; y[a] = f.y[b]; y[b] = f.y[a]
            v[a] = f.visibility[b]; v[b] = f.visibility[a]
        }
        PoseFrame(f.index, f.t, x, y, v, f.found)
    }, t.width, t.height)

    private fun mirrorCrop(c: GrayCrop, width: Int): GrayCrop {
        val px = ByteArray(c.w * c.h)
        for (y in 0 until c.h) for (x in 0 until c.w) px[y * c.w + (c.w - 1 - x)] = c.pixels[y * c.w + x]
        return GrayCrop(c.frameIndex, width - (c.x0 + c.w), c.y0, c.w, c.h, px)
    }

    /** Moves hips toward the ball (+x here) by up to 0.2 T and lifts the shoulders back, from the top to impact. */
    private fun withEarlyExtension(t: PoseTrack, prep: Prepared): PoseTrack {
        val top = prep.events.handsHighest; val imp = prep.events.impact
        val torso = prep.track.torso
        return PoseTrack(t.frames.mapIndexed { i, f ->
            val a = when { i <= top -> 0.0; i >= imp -> 1.0; else -> (i - top).toDouble() / (imp - top) }
            val x = f.x.copyOf()
            for (lm in listOf(Lm.L_HIP, Lm.R_HIP)) x[lm] += 0.2 * torso * a
            for (lm in listOf(Lm.L_SHOULDER, Lm.R_SHOULDER, Lm.L_EAR, Lm.R_EAR, Lm.NOSE)) x[lm] -= 0.1 * torso * a
            PoseFrame(f.index, f.t, x, f.y.copyOf(), f.visibility, f.found)
        }, t.width, t.height)
    }

    private fun cut(c: GrayCrop, r: CropRequest): GrayCrop {
        val x0 = maxOf(r.x0, c.x0); val y0 = maxOf(r.y0, c.y0)
        val x1 = minOf(r.x0 + r.w, c.x0 + c.w); val y1 = minOf(r.y0 + r.h, c.y0 + c.h)
        val w = x1 - x0; val h = y1 - y0
        val px = ByteArray(w * h)
        for (y in 0 until h) System.arraycopy(c.pixels, (y + y0 - c.y0) * c.w + (x0 - c.x0), px, y * w, w)
        return GrayCrop(c.frameIndex, x0, y0, w, h, px)
    }

    fun checks(): List<Check> {
        val k = Knowledge.loadBundled()
        val (prep, res) = analyze(k)
        val ev = prep.events
        val fps = 30.0
        val out = ArrayList<Check>()
        fun check(name: String, ok: Boolean, detail: String) = out.add(Check(name, ok, detail))
        fun frameOf(t: Double?) = t?.let { it * fps }

        val startF = frameOf(res.eventTimes["start"])
        check("First movement at frame 20 ±2", startF != null && startF in 17.5..22.5, "frame %.1f".format(startF))
        val downF = frameOf(res.eventTimes["clubStartsDown"])
        check("Club starts down at frame 51–52 ±2", downF != null && downF in 49.0..54.0, "frame ${downF?.let { "%.2f".format(it) }}")
        val impF = frameOf(res.eventTimes["impact"])
        check("Impact between frames 60 and 61 ±1", impF != null && impF in 59.0..62.0, "frame %.2f".format(impF))
        check("Finish reached at frames 74–78 ±4", ev.finish != null && ev.finish in 70..82, "frame ${ev.finish}")
        val tempo = res.tempo
        check("Tempo measured from the club, 3.2–3.8:1", tempo?.fromClub == true && tempo.ratio != null && tempo.ratio in 3.2..3.8,
            "ratio ${tempo?.ratio?.let { "%.2f".format(it) }}, from club ${tempo?.fromClub}")

        // Shaft angles against the Python/OpenCV segment detector on the frames where its lines lay on the
        // shaft (on 46–48 they partly followed the forearm, see prove/shaft_top.jpg, so those aren't used).
        val reference = mapOf(49 to 58.9, 50 to 55.7, 51 to 60.8, 52 to 64.0, 53 to 75.6, 54 to 81.3)
        val diffs = reference.mapNotNull { (f, a) -> res.shaftReadings[f]?.let { f to it - a } }
        val foundTop = (44..55).count { it in res.shaftReadings }
        check("Shaft found on at least 10 of frames 44–55", foundTop >= 10, "found $foundTop: ${res.shaftReadings.toSortedMap().mapValues { "%.1f".format(it.value) }}")
        check("Shaft angle within 6° of the reference", diffs.all { abs(it.second) <= 6.0 },
            diffs.joinToString { "${it.first}: %+.1f".format(it.second) })

        val g = prep.guess
        check("Guessed down-the-line, right-handed", g?.view == CameraView.DOWN_THE_LINE && g.handedness == Handedness.RIGHT, "$g")
        check("No view or handedness warnings", res.warnings.none { "looks like" in it || "looks left" in it || "looks right" in it }, res.warnings.joinToString(" | "))

        val metrics = res.metrics
        val findings = res.findings
        val byId = findings.associateBy { it.rule.id }
        for (id in listOf("A1", "A2", "B1", "C1", "C2", "B4")) {
            val f = byId.getValue(id)
            check("$id ${f.rule.name} passes on the sample", f.status == Status.PASS, "${f.status} value ${f.value?.let { "%.3f".format(it) }}")
        }
        check("Address bend measured 25–32°", (metrics["address_bend"]?.value ?: 0.0) in 25.0..32.0, "${metrics["address_bend"]}")

        val text = res.feedback.feedbackText()
        check("Feedback text produced", text.length > 40 && res.feedback.tempo != null, text.lines().first())
        val round = AnalysisResult.fromJson(res.toJson())
        check("Analysis survives a JSON round trip", round.feedback == res.feedback && round.track.size == res.track.size, "")
        val note = Note.fromAnalysis("n1", 0L, res, "Felt smooth", false)
        val exported = note.exportText()
        check("Note export records the club", "Club:    7-iron" in exported && "MY COMMENT\nFelt smooth" in exported, "")
        check("Note survives a JSON round trip", Note.fromJson(note.toJson()) == note, "")

        // Synthetic fault: push the hips toward the ball and stand the torso up through the downswing.
        val ee = analyzeTrack(withEarlyExtension(loadTrack(), prep), Handedness.RIGHT, k)
        val eeIds = ee.findings.filter { it.status == Status.FAULT || it.status == Status.WARN }.map { it.rule.id }
        check("Injected early extension is flagged (C1)", "C1" in eeIds, "flagged $eeIds")
        check("Injected early extension leads the advice", ee.feedback.workOn.firstOrNull()?.faultId == "early_extension",
            ee.feedback.workOn.joinToString { it.faultId })

        // Left-handed: mirror the clip and swap left/right labels; results should match the right-handed run.
        val lefty = analyzeTrack(mirrored(loadTrack()), Handedness.LEFT, k)
        val same = listOf("start", "clubStartsDown", "impact").all { key ->
            val a = res.eventTimes[key]; val b = lefty.eventTimes[key]
            a != null && b != null && abs(a - b) < 0.02
        }
        check("Mirrored left-handed clip gives the same events", same, "${lefty.eventTimes}")
        check("Mirrored left-handed clip gives the same tempo (±0.15)", lefty.tempo?.ratio?.let { abs(it - (res.tempo?.ratio ?: 0.0)) < 0.15 } == true,
            "${lefty.tempo?.ratio}")
        check("Mirrored clip is guessed left-handed", SwingAnalyzer.prepare(request(mirrored(loadTrack()), Handedness.LEFT)).guess?.handedness == Handedness.LEFT, "")
        return out
    }
}
