package dev.swinganalyzer.core

import java.util.Locale
import kotlin.math.abs

data class WorkOnItem(
    val ruleId: String,
    val faultId: String,
    val title: String,
    val seen: String,
    val why: String,
    val drillName: String?,
    val drillSteps: List<String>,
    val severe: Boolean,
)

data class NumberRow(val name: String, val value: String, val expected: String, val status: Status, val unusual: Boolean = false)

data class TempoInfo(
    val back: Double?,
    val down: Double?,
    val ratio: Double?,
    val fromClub: Boolean,
    val rangeLow: Double? = null,
    val rangeHigh: Double? = null,
)

data class Feedback(
    val summary: String,
    val working: List<String>,
    val workOn: List<WorkOnItem>,
    val tempo: String?,
    val couldntJudge: List<String>,
    val numbers: List<NumberRow>,
    val bodyNote: String?,
) {
    /** The feedback as plain text, exactly as the app shows it and as notes export it. */
    fun feedbackText(): String = buildString {
        appendLine(summary)
        if (working.isNotEmpty()) {
            appendLine()
            appendLine("What's working")
            working.forEach { appendLine("• $it") }
        }
        if (workOn.isNotEmpty()) {
            appendLine()
            appendLine("Work on")
            workOn.forEachIndexed { i, w ->
                appendLine("${i + 1}. ${w.title}")
                appendLine("   Seen: ${w.seen}")
                appendLine("   Why it matters: ${w.why}")
                if (w.drillName != null) {
                    appendLine("   Drill: ${w.drillName}")
                    w.drillSteps.forEach { appendLine("     - $it") }
                }
            }
        }
        if (bodyNote != null) {
            appendLine()
            appendLine(bodyNote)
        }
    }.trimEnd()

    fun numbersText(): String = numbers.joinToString("\n") { r ->
        val exp = if (r.expected.isBlank()) "" else "  (expected ${r.expected})"
        val flag = when {
            r.status == Status.FAULT -> "  ✗"
            r.status == Status.WARN -> "  ⚠"
            r.unusual -> "  (unusual)"
            else -> ""
        }
        "${r.name}: ${r.value}$exp$flag"
    }

    fun couldntJudgeText(): String = couldntJudge.joinToString("\n") { "• $it" }
}

/** Turns numbers into words for the user, using their height to convert torso lengths to centimetres. */
class Formatter(heightCm: Double) {
    private val torsoCm = 0.29 * heightCm

    fun cm(t: Double) = "%.0f cm".format(Locale.US, abs(t) * torsoCm)

    /** A value as a magnitude for sentences ("about 4 cm", "12°"). */
    fun magnitude(metric: String, unit: String, v: Double): String = when {
        metric == "tempo_ratio" -> "%.1f:1".format(Locale.US, v)
        metric == "stance_ratio" -> "%.2f×".format(Locale.US, v)
        unit == "T" -> "about ${cm(v)}"
        unit == "deg" -> "%.0f°".format(Locale.US, abs(v))
        unit == "ratio" || unit == "pct" || unit == "share" -> "%.0f%%".format(Locale.US, v * 100)
        unit == "s" -> "%.2f s".format(Locale.US, v)
        unit == "flag" -> if (v >= 0.5) "yes" else "no"
        else -> "%.2f".format(Locale.US, v)
    }

    /** A value for the numbers table, keeping its sign where direction matters. */
    fun signed(metric: String, unit: String, v: Double): String = when (unit) {
        "T" -> if (abs(v) * torsoCm < 0.5) "0 cm" else (if (v >= 0) "+" else "−") + cm(v)
        "deg" -> if (abs(v) < 0.5) "0°" else (if (v < 0) "−" else "") + "%.0f°".format(Locale.US, abs(v))
        else -> magnitude(metric, unit, v)
    }

    fun expected(rule: Rule, range: Pair<Double?, Double?>?): String {
        if (range == null) return ""
        if (rule.unit == "flag") return "yes"
        val (lo, hi) = range
        fun f(x: Double) = signed(rule.metric, rule.unit, x)
        return when {
            lo != null && hi != null -> "${f(lo)} to ${f(hi)}"
            lo != null -> if (rule.isInfoOnly) "usually above ${f(lo)}" else "above ${f(lo)}"
            hi != null -> if (rule.isInfoOnly) "usually below ${f(hi)}" else "below ${f(hi)}"
            else -> ""
        }
    }
}

object Coach {

    private val categoryOrder = listOf("setup", "pivot", "shift", "finish", "tempo")

    fun build(
        knowledge: Knowledge,
        findings: List<Finding>,
        club: Club,
        shot: ShotType,
        view: CameraView,
        tempo: TempoInfo?,
        heightCm: Double,
    ): Feedback {
        val fmt = Formatter(heightCm)
        fun fill(template: String, f: Finding): String {
            val r = f.rule
            val exp = f.expected
            return template
                .replace("{value}", f.value?.let { fmt.magnitude(r.metric, r.unit, it) } ?: "")
                .replace("{limit}", f.limit?.let { fmt.magnitude(r.metric, r.unit, it) } ?: "")
                .replace("{expected}", exp?.let { (lo, hi) ->
                    if (lo != null && hi != null) "${fmt.magnitude(r.metric, r.unit, lo)}–${fmt.magnitude(r.metric, r.unit, hi)}" else ""
                } ?: "")
                .replace("{club}", club.name.lowercase(Locale.US))
        }

        // Work on: faults before warnings, then setup → pivot → shift → finish → tempo, then by severity.
        val problems = findings.filter { (it.status == Status.WARN || it.status == Status.FAULT) && it.faultId != null }
            .sortedWith(compareBy<Finding>({ if (it.status == Status.FAULT) 0 else 1 },
                { categoryOrder.indexOf(it.rule.category) }, { -it.severity * it.confidence }))
            .distinctBy { it.faultId }
        val workOn = problems.take(3).map { f ->
            val text = knowledge.faults.getValue(f.faultId!!)
            val drill = text.drills.firstOrNull()?.let { knowledge.drills[it] }
            WorkOnItem(f.rule.id, f.faultId, text.name, fill(text.seen, f), text.why, drill?.name,
                drill?.steps ?: emptyList(), f.status == Status.FAULT)
        }
        val bodyNote = if (problems.take(3).any { knowledge.faults[it.faultId]?.bodyNote == true }) knowledge.bodyNote else null

        // What's working: the most reliable passed check from each part of the swing, in swing order.
        val praised = findings.filter { it.status == Status.PASS && it.rule.praise != null && it.confidence >= 0.45 }
        val working = categoryOrder.mapNotNull { cat -> praised.filter { it.rule.category == cat }.maxByOrNull { it.confidence } }
            .take(if (problems.isEmpty()) 4 else 2)
            .map { fill(it.rule.praise!!, it) }

        val otherView = if (view == CameraView.DOWN_THE_LINE) "face-on" else "down-the-line"
        val summary = when {
            workOn.isEmpty() -> "Nothing stood out on the checks the app can make from this view. Try a $otherView clip to check the rest."
            workOn.size == 1 -> "Main thing to work on: ${workOn[0].title.lowercase(Locale.US)}."
            else -> "Main thing to work on: ${workOn[0].title.lowercase(Locale.US)}. Also worth a look: " +
                workOn.drop(1).joinToString(" and ") { it.title.lowercase(Locale.US) } + "."
        }

        val tempoText = tempo?.let { tempoLine(it, shot) }

        val wrongView = findings.filter { it.status == Status.WRONG_VIEW }.map { it.rule.name }
        val unclear = findings.filter { it.status == Status.NOT_JUDGED }.map { "${it.rule.name} (${it.reason})" }
        val couldnt = buildList {
            if (wrongView.isNotEmpty()) add("Needs a $otherView clip: ${wrongView.joinToString(", ")}.")
            unclear.forEach { add("Couldn't judge: $it.") }
            add("Not measurable by the app yet: ${knowledge.notJudged.joinToString(", ")}.")
        }

        val numbers = findings.filter { it.value != null && it.status != Status.NOT_JUDGED && it.status != Status.WRONG_VIEW }
            .map { f ->
                NumberRow(f.rule.name, fmt.signed(f.rule.metric, f.rule.unit, f.value!!), fmt.expected(f.rule, f.expected),
                    f.status, unusual = f.status == Status.INFO && f.severity > 0)
            }
        return Feedback(summary, working, workOn, tempoText, couldnt, numbers, bodyNote)
    }

    private fun tempoLine(t: TempoInfo, shot: ShotType): String {
        val ref = if (shot == ShotType.FULL) "good players are close to 3:1" else "about 2:1 is the reference"
        return if (t.ratio != null && t.back != null && t.down != null) {
            val label = if (shot == ShotType.FULL) "down" else "through"
            "Tempo %.1f:1 — %.2f s back, %.2f s %s, %.2f s in total (%s).".format(Locale.US, t.ratio, t.back, t.down, label, t.back + t.down, ref)
        } else if (t.rangeLow != null && t.rangeHigh != null) {
            "Tempo couldn't be pinned down because the club wasn't found at the top. From your hands alone it's between %.1f:1 and %.1f:1 (%s)."
                .format(Locale.US, t.rangeLow, t.rangeHigh, ref)
        } else "Tempo couldn't be measured in this clip."
    }
}
