package org.wip.plugintoolkit.features.plugin.logic

data class ChangelogVersion(
    val date: String,
    val version: String,
    val tags: Map<String, List<String>>
)

object ChangelogParser {
    private val SEPARATOR = "-".repeat(100)

    fun parse(content: String): List<ChangelogVersion> {
        val versions = mutableListOf<ChangelogVersion>()
        val lines = content.lines()

        var currentDate = ""
        var currentVersion = ""
        var currentTags = mutableMapOf<String, MutableList<String>>()
        var currentTag = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 100-Dash Separator is the ONLY way to finalize a version block before the end of file
            if (trimmed == SEPARATOR) {
                if (currentVersion.isNotEmpty()) {
                    versions.add(ChangelogVersion(currentDate, currentVersion, currentTags))
                    currentDate = ""
                    currentVersion = ""
                    currentTags = mutableMapOf()
                    currentTag = ""
                }
                continue
            }

            if (trimmed.startsWith("Version:", ignoreCase = true)) {
                currentVersion = trimmed.removePrefix("Version:").trim()
                continue
            }

            if (trimmed.startsWith("Date:", ignoreCase = true)) {
                currentDate = trimmed.removePrefix("Date:").trim()
                continue
            }

            if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.endsWith(":")) {
                // New tag
                currentTag = trimmed.removeSuffix(":")
                currentTags[currentTag] = mutableListOf()
            } else if (currentTag.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                // Voice under tag
                val voice = trimmed.trim().removePrefix("-").trim()
                if (voice.isNotEmpty()) {
                    currentTags.getOrPut(currentTag) { mutableListOf() }.add(voice)
                }
            }
        }

        // Add the last one
        if (currentVersion.isNotEmpty()) {
            versions.add(ChangelogVersion(currentDate, currentVersion, currentTags))
        }

        return versions
    }
}
