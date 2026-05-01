package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

data class ChangelogVersion(
    val date: String,
    val version: String,
    val tags: Map<String, List<String>>
)

object ChangelogParser {
    fun parse(content: String): List<ChangelogVersion> {
        val versions = mutableListOf<ChangelogVersion>()
        val lines = content.lines()

        var currentDate = ""
        var currentVersion = ""
        var currentTags = mutableMapOf<String, MutableList<String>>()
        var currentTag = ""

        val versionHeaderRegex = Regex("""\[(.*?)\]\s*(?:-|\[)?\s*(.*?)(?:\])?$""")

        for (line in lines) {
            val trimmed = line.trimEnd()
            if (trimmed.isEmpty()) continue
            if (trimmed.all { it == '-' || it == '=' || it == '*' }) continue

            val headerMatch = versionHeaderRegex.find(trimmed)
            if (headerMatch != null) {
                // Save previous version
                if (currentVersion.isNotEmpty()) {
                    versions.add(ChangelogVersion(currentDate, currentVersion, currentTags))
                }

                // Start new version
                currentDate = headerMatch.groupValues[1].trim()
                currentVersion = headerMatch.groupValues[2].removePrefix("Version:").trim()
                currentTags = mutableMapOf()
                currentTag = ""
                continue
            }

            if (trimmed.startsWith("Version:", ignoreCase = true)) {
                if (currentVersion.isNotEmpty()) {
                    versions.add(ChangelogVersion(currentDate, currentVersion, currentTags))
                }
                currentVersion = trimmed.removePrefix("Version:").trim()
                currentDate = ""
                currentTags = mutableMapOf()
                currentTag = ""
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
                    currentTags[currentTag]?.add(voice)
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
