package org.wip.plugintoolkit.core.utils

object VersionUtils {

    /**
     * Compares two semantic versions, supporting optional pre-release tags such as `-alpha01`.
     * @return 0 if equal, 1 if v1 > v2, -1 if v1 < v2
     */
    fun compare(v1: String, v2: String): Int {
        val parsed1 = parse(v1)
        val parsed2 = parse(v2)

        for (i in 0 until maxOf(parsed1.core.size, parsed2.core.size)) {
            val p1 = parsed1.core.getOrElse(i) { 0 }
            val p2 = parsed2.core.getOrElse(i) { 0 }

            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }

        return comparePreRelease(parsed1.preRelease, parsed2.preRelease)
    }

    private data class ParsedVersion(
        val core: List<Int>,
        val preRelease: List<String>
    )

    private fun parse(version: String): ParsedVersion {
        val normalized = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
        val corePart = normalized.substringBefore("-")
        val preReleasePart = normalized.substringAfter("-", "")
        val core = corePart.split(".").mapNotNull { it.toIntOrNull() }
        val preRelease = if (preReleasePart.isBlank()) {
            emptyList()
        } else {
            preReleasePart.split(".").filter { it.isNotBlank() }
        }
        return ParsedVersion(core = core, preRelease = preRelease)
    }

    private fun comparePreRelease(pre1: List<String>, pre2: List<String>): Int {
        if (pre1.isEmpty() && pre2.isEmpty()) return 0
        if (pre1.isEmpty()) return 1
        if (pre2.isEmpty()) return -1

        for (i in 0 until maxOf(pre1.size, pre2.size)) {
            if (i >= pre1.size) return -1
            if (i >= pre2.size) return 1
            val cmp = compareIdentifier(pre1[i], pre2[i])
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun compareIdentifier(id1: String, id2: String): Int {
        val tokens1 = tokenizeIdentifier(id1)
        val tokens2 = tokenizeIdentifier(id2)
        for (i in 0 until maxOf(tokens1.size, tokens2.size)) {
            if (i >= tokens1.size) return -1
            if (i >= tokens2.size) return 1
            val t1 = tokens1[i]
            val t2 = tokens2[i]
            val n1 = t1.toLongOrNull()
            val n2 = t2.toLongOrNull()
            val cmp = when {
                n1 != null && n2 != null -> n1.compareTo(n2)
                n1 != null -> -1
                n2 != null -> 1
                else -> t1.compareTo(t2, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun tokenizeIdentifier(identifier: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var currentIsDigit: Boolean? = null
        for (ch in identifier) {
            val isDigit = ch.isDigit()
            if (currentIsDigit != null && currentIsDigit != isDigit) {
                tokens.add(current.toString())
                current.clear()
            }
            current.append(ch)
            currentIsDigit = isDigit
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /**
     * Checks if a version is at least the required version.
     */
    fun isAtLeast(version: String, required: String): Boolean {
        return compare(version, required) >= 0
    }

    /**
     * Checks if a version is at most the maximum version.
     */
    fun isAtMost(version: String, maximum: String): Boolean {
        return compare(version, maximum) <= 0
    }

    /**
     * Checks if a version is within a range (inclusive).
     */
    fun isWithinRange(version: String, min: String, max: String): Boolean {
        return isAtLeast(version, min) && isAtMost(version, max)
    }
}
