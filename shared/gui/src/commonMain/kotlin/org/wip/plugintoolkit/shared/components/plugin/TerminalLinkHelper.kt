package org.wip.plugintoolkit.shared.components.plugin

import org.wip.plugintoolkit.core.utils.PlatformUtils

data class TerminalLinkSpan(
    val start: Int,
    val end: Int,
    val target: String,
    val isUrl: Boolean
)

object PathExistenceChecker {
    private val cache = object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 500
        }
    }

    fun exists(path: String): Boolean {
        if (path.isBlank()) return false
        synchronized(cache) {
            cache[path]?.let { return it }
        }
        val exists = try {
            PlatformUtils.exists(path)
        } catch (_: Throwable) {
            false
        }
        synchronized(cache) {
            cache[path] = exists
        }
        return exists
    }

    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
    }
}

object TerminalLinkHelper {
    private val URL_REGEX = Regex("""https?://[^\s)\]>"']+""")
    private val QUOTED_PATH_REGEX = Regex("""["']([a-zA-Z]:[/\\][^"']+|\\\\[^"']+|/(?:Users|home|tmp|var|etc|opt|usr|Applications|mnt|Volumes)/[^"']+)["']""")
    private val PATH_PREFIX_REGEX = Regex("""(?:^|(?<=[\s(\[<:="']))([a-zA-Z]:[/\\]|\\\\|/(?:Users|home|tmp|var|etc|opt|usr|Applications|mnt|Volumes)/)""")
    private val BOUNDARY_REGEX = Regex("""(?:\s--[a-zA-Z]|\s-[a-zA-Z]|\s[a-zA-Z]:[/\\]|\s/(?:Users|home|tmp|var|etc|opt|usr|Applications|mnt|Volumes)/|\s["']|$)""")

    fun findLinkSpans(
        line: String,
        pathExists: (String) -> Boolean = { PathExistenceChecker.exists(it) }
    ): List<TerminalLinkSpan> {
        val spans = mutableListOf<TerminalLinkSpan>()

        // 1. Find URLs
        for (match in URL_REGEX.findAll(line)) {
            val cleanTarget = match.value.trimEnd('.', ',', ';', ':', ')', ']')
            val end = match.range.first + cleanTarget.length
            spans.add(TerminalLinkSpan(match.range.first, end, cleanTarget, isUrl = true))
        }

        // 2. Find Quoted Paths
        for (match in QUOTED_PATH_REGEX.findAll(line)) {
            val rawPath = match.groupValues[1]
            val cleanPath = rawPath.trimEnd('.', ',', ';', ':', ')', ']')
            if (pathExists(cleanPath)) {
                val start = match.range.first
                val end = match.range.last + 1
                val overlaps = spans.any { it.start < end && start < it.end }
                if (!overlaps) {
                    spans.add(TerminalLinkSpan(start, end, cleanPath, isUrl = false))
                }
            }
        }

        // 3. Find Unquoted Paths (handling spaces and command-line argument boundaries)
        for (match in PATH_PREFIX_REGEX.findAll(line)) {
            val prefixGroup = match.groups[1] ?: continue
            val start = prefixGroup.range.first

            val alreadyCovered = spans.any { it.start <= start && start < it.end }
            if (alreadyCovered) continue

            // Determine maximum substring to inspect before the next boundary
            val remainingFromStart = line.substring(start)
            val boundaryMatch = BOUNDARY_REGEX.find(remainingFromStart)
            val boundaryIndex = if (boundaryMatch != null && boundaryMatch.range.first > 0) {
                start + boundaryMatch.range.first
            } else {
                line.length
            }

            val rawSlice = line.substring(start, boundaryIndex)
            var candidate = rawSlice.trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')

            // Try candidate, then trim word-by-word if candidate has spaces
            var matchedCandidate: String? = null
            while (candidate.isNotBlank()) {
                if (pathExists(candidate)) {
                    matchedCandidate = candidate
                    break
                }
                if (!candidate.contains(' ')) {
                    break
                }
                candidate = candidate.substringBeforeLast(' ').trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')
            }

            if (matchedCandidate != null) {
                val end = start + matchedCandidate.length
                val overlaps = spans.any { it.start < end && start < it.end }
                if (!overlaps) {
                    spans.add(TerminalLinkSpan(start, end, matchedCandidate, isUrl = false))
                }
            }
        }

        return spans.sortedBy { it.start }
    }
}
