package org.wip.plugintoolkit.core.utils

object VersionUtils {
    
    /**
     * Compares two semantic versions.
     * @return 0 if equal, 1 if v1 > v2, -1 if v1 < v2
     */
    fun compare(v1: String, v2: String): Int {
        val s1 = v1.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val s2 = v2.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until maxOf(s1.size, s2.size)) {
            val p1 = s1.getOrElse(i) { 0 }
            val p2 = s2.getOrElse(i) { 0 }
            
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
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
