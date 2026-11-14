package dev.swinganalyzer.core

/** A club from the bag. [position] runs from 0 (lob wedge) through 0.5 (7-iron) to 1 (driver). */
data class Club(
    val id: String,
    val name: String,
    val family: String,
    val position: Double,
    val shots: List<ShotType>,
)

/** A one-sided threshold: crossing [warn] is a warning, crossing [fault] a fault. */
data class Band(val warn: Double, val fault: Double?, val faultId: String)

data class Rule(
    val id: String,
    val name: String,
    val metric: String,
    val unit: String,
    val view: String,
    val shots: Set<ShotType>,
    val category: String,
    val reliability: Double,
    val profile: String?,
    val range: List<Double>?,
    val faultMargin: Double?,
    val lowFault: String?,
    val highFault: String?,
    val high: Band?,
    val low: Band?,
    val infoLow: Double?,
    val infoHigh: Double?,
    val praise: String?,
) {
    fun appliesTo(view: CameraView) = this.view == "any" || this.view == view.id
    val isInfoOnly get() = profile == null && range == null && high == null && low == null
}

data class FaultText(
    val id: String,
    val name: String,
    val seen: String,
    val why: String,
    val drills: List<String>,
    val bodyNote: Boolean,
)

data class Drill(val id: String, val name: String, val steps: List<String>)

class Knowledge(
    val version: String,
    val clubs: List<Club>,
    private val profiles: Map<String, Map<String, List<Double>>>,
    val rules: List<Rule>,
    val faults: Map<String, FaultText>,
    val drills: Map<String, Drill>,
    val notJudged: List<String>,
    val bodyNote: String,
) {
    fun club(id: String) = clubs.first { it.id == id }

    /** Expected range for a profile metric, interpolated between the wedge, 7-iron and driver anchors. */
    fun profileRange(profile: String, club: Club): Pair<Double, Double> {
        val p = profiles[profile] ?: error("Unknown profile $profile")
        val w = p.getValue("wedge"); val i = p.getValue("iron7"); val d = p.getValue("driver")
        val pos = club.position.coerceIn(0.0, 1.0)
        val (a, b, f) = if (pos <= 0.5) Triple(w, i, pos / 0.5) else Triple(i, d, (pos - 0.5) / 0.5)
        return Pair(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)
    }

    companion object {
        /** Loads the bundled knowledge.json from the classpath (works on the JVM and on Android). */
        fun loadBundled(): Knowledge {
            val stream = Knowledge::class.java.getResourceAsStream("/knowledge.json")
                ?: error("knowledge.json not found on the classpath")
            return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        fun parse(text: String): Knowledge {
            val root = Json.parse(text).obj()
            val clubs = root["clubs"].arr().map { c ->
                val o = c.obj()
                Club(o.str("id"), o.str("name"), o.str("family"), o.num("position"),
                    o.strList("shots").map { ShotType.fromId(it) })
            }
            val profiles = root["profiles"].obj().mapValues { (_, v) ->
                v.obj().mapValues { (_, r) -> r.arr().map { (it as Number).toDouble() } }
            }
            fun band(o: Map<String, Any?>?): Band? = o?.let {
                Band(it.num("warn"), it.numOrNull("fault"), it.str("faultId"))
            }
            val rules = root["rules"].arr().map { r ->
                val o = r.obj()
                Rule(
                    id = o.str("id"), name = o.str("name"), metric = o.str("metric"), unit = o.str("unit"),
                    view = o.str("view"), shots = o.strList("shots").map { ShotType.fromId(it) }.toSet(),
                    category = o.str("category"), reliability = o.num("reliability"),
                    profile = o.strOrNull("profile"), range = o["range"]?.let { o.numList("range") },
                    faultMargin = o.numOrNull("faultMargin"), lowFault = o.strOrNull("lowFault"),
                    highFault = o.strOrNull("highFault"), high = band(o.objOrNull("high")),
                    low = band(o.objOrNull("low")), infoLow = o.numOrNull("infoLow"),
                    infoHigh = o.numOrNull("infoHigh"), praise = o.strOrNull("praise"),
                )
            }
            val faults = root["faults"].obj().mapValues { (id, v) ->
                val o = v.obj()
                FaultText(id, o.str("name"), o.str("seen"), o.str("why"), o.strList("drills"), o.bool("bodyNote"))
            }
            val drills = root["drills"].obj().mapValues { (id, v) ->
                val o = v.obj()
                Drill(id, o.str("name"), o.strList("steps"))
            }
            val k = Knowledge(root.str("version"), clubs, profiles, rules, faults, drills,
                root.strList("notJudged"), root.str("bodyNote"))
            k.validate()
            return k
        }
    }

    /** Fails fast if the knowledge file references something that doesn't exist. */
    private fun validate() {
        for (r in rules) {
            listOfNotNull(r.lowFault, r.highFault, r.high?.faultId, r.low?.faultId).forEach {
                require(it in faults) { "Rule ${r.id} references unknown fault $it" }
            }
            r.profile?.let { require(it in profiles) { "Rule ${r.id} references unknown profile $it" } }
        }
        for (f in faults.values) f.drills.forEach { require(it in drills) { "Fault ${f.id} references unknown drill $it" } }
    }
}
