package dev.swinganalyzer.core

import kotlin.math.abs
import kotlin.math.max

enum class Status { PASS, INFO, WARN, FAULT, NOT_JUDGED, WRONG_VIEW }

data class Finding(
    val rule: Rule,
    val status: Status,
    val value: Double?,
    val confidence: Double,
    /** Expected range for this club, if the rule has one. */
    val expected: Pair<Double?, Double?>?,
    val faultId: String?,
    /** The threshold that was crossed, for messages like "keep it under …". */
    val limit: Double?,
    /** How far past the threshold, scaled so 1 ≈ the gap between warning and fault. */
    val severity: Double,
    val reason: String? = null,
)

object RuleEngine {

    const val MIN_CONFIDENCE = 0.3

    fun evaluate(
        knowledge: Knowledge,
        metrics: Map<String, Measured>,
        club: Club,
        shot: ShotType,
        view: CameraView,
    ): List<Finding> = knowledge.rules.filter { shot in it.shots }.map { rule ->
        if (!rule.appliesTo(view)) return@map Finding(rule, Status.WRONG_VIEW, null, 0.0, null, null, null, 0.0)
        val m = metrics[rule.metric]
            ?: return@map Finding(rule, Status.NOT_JUDGED, null, 0.0, null, null, null, 0.0, "not measurable in this clip")
        val conf = m.confidence * rule.reliability
        if (conf < MIN_CONFIDENCE)
            return@map Finding(rule, Status.NOT_JUDGED, m.value, conf, null, null, null, 0.0, "body landmarks unclear")
        judge(knowledge, rule, m.value, conf, club)
    }

    private fun judge(k: Knowledge, rule: Rule, v: Double, conf: Double, club: Club): Finding {
        // Range rules (club profile or fixed range).
        val range = rule.profile?.let { k.profileRange(it, club) }
            ?: rule.range?.let { Pair(it[0], it[1]) }
        if (range != null) {
            val (lo, hi) = range
            val margin = rule.faultMargin ?: Double.MAX_VALUE
            val scale = minOf(margin, (hi - lo).coerceAtLeast(1e-6))
            return when {
                v < lo -> Finding(rule, if (lo - v > margin) Status.FAULT else Status.WARN, v, conf, Pair(lo, hi), rule.lowFault, lo, (lo - v) / scale)
                v > hi -> Finding(rule, if (v - hi > margin) Status.FAULT else Status.WARN, v, conf, Pair(lo, hi), rule.highFault, hi, (v - hi) / scale)
                else -> Finding(rule, Status.PASS, v, conf, Pair(lo, hi), null, null, 0.0)
            }
        }
        rule.high?.let { b ->
            if (v > b.warn) {
                val isFault = b.fault != null && v > b.fault
                val scale = if (b.fault != null) abs(b.fault - b.warn) else max(abs(b.warn) * 0.5, 1e-6)
                return Finding(rule, if (isFault) Status.FAULT else Status.WARN, v, conf, expected(rule), b.faultId, b.warn, (v - b.warn) / scale)
            }
        }
        rule.low?.let { b ->
            if (v < b.warn) {
                val isFault = b.fault != null && v < b.fault
                val scale = if (b.fault != null) abs(b.warn - b.fault) else max(abs(b.warn) * 0.5, 1e-6)
                return Finding(rule, if (isFault) Status.FAULT else Status.WARN, v, conf, expected(rule), b.faultId, b.warn, (b.warn - v) / scale)
            }
        }
        if (rule.isInfoOnly) {
            val flagged = (rule.infoLow != null && v < rule.infoLow) || (rule.infoHigh != null && v > rule.infoHigh)
            return Finding(rule, Status.INFO, v, conf, Pair(rule.infoLow, rule.infoHigh), null, null, if (flagged) 1.0 else 0.0,
                if (flagged) "outside the usual range" else null)
        }
        return Finding(rule, Status.PASS, v, conf, expected(rule), null, null, 0.0)
    }

    /** The "good" range implied by a rule's warning thresholds. */
    private fun expected(rule: Rule): Pair<Double?, Double?> = Pair(rule.low?.warn, rule.high?.warn)
}
