package dev.swinganalyzer.core

/**
 * Minimal JSON reader and writer so the core has no third-party dependencies.
 * Values map to: Map<String, Any?>, List<Any?>, String, Double, Boolean, null.
 */
object Json {

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        require(p.i == text.length) { "Trailing characters at ${p.i}" }
        return v
    }

    fun write(value: Any?, indent: Boolean = true): String {
        val sb = StringBuilder()
        writeValue(sb, value, if (indent) 0 else -1)
        return sb.toString()
    }

    private fun writeValue(sb: StringBuilder, v: Any?, level: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Number -> {
                val d = v.toDouble()
                if (d.isNaN() || d.isInfinite()) sb.append("null")
                else if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) sb.append(d.toLong())
                else sb.append(d.toString())
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    newline(sb, level + 1)
                    writeString(sb, k.toString())
                    sb.append(if (level >= 0) ": " else ":")
                    writeValue(sb, value, if (level >= 0) level + 1 else -1)
                }
                if (!first) newline(sb, level)
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    newline(sb, level + 1)
                    writeValue(sb, item, if (level >= 0) level + 1 else -1)
                }
                if (!first) newline(sb, level)
                sb.append(']')
            }
            is DoubleArray -> writeValue(sb, v.toList(), level)
            is IntArray -> writeValue(sb, v.toList(), level)
            else -> writeString(sb, v.toString())
        }
    }

    private fun newline(sb: StringBuilder, level: Int) {
        if (level < 0) return
        sb.append('\n')
        repeat(level) { sb.append("  ") }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipWs()
            require(i < s.length) { "Unexpected end of JSON" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("Unexpected '$c' at $i")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "Expected $word at $i" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                require(s[i] == ':') { "Expected ':' at $i" }
                i++
                m[k] = value()
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> error("Expected ',' or '}' at $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            skipWs()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> error("Expected ',' or ']' at $i")
                }
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "Expected string at $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> error("Bad escape '$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(start, i).toDouble()
        }
    }
}

// Small typed accessors used when reading parsed JSON.
@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?> = this as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Any?.arr(): List<Any?> = this as List<Any?>

internal fun Map<String, Any?>.str(key: String): String = this[key] as String
internal fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as String?
internal fun Map<String, Any?>.num(key: String): Double = (this[key] as Number).toDouble()
internal fun Map<String, Any?>.numOrNull(key: String): Double? = (this[key] as Number?)?.toDouble()
internal fun Map<String, Any?>.bool(key: String, default: Boolean = false): Boolean = (this[key] as Boolean?) ?: default
internal fun Map<String, Any?>.objOrNull(key: String): Map<String, Any?>? = this[key]?.obj()
internal fun Map<String, Any?>.strList(key: String): List<String> = (this[key] as List<*>?)?.map { it as String } ?: emptyList()
internal fun Map<String, Any?>.numList(key: String): List<Double> = (this[key] as List<*>?)?.map { (it as Number).toDouble() } ?: emptyList()
