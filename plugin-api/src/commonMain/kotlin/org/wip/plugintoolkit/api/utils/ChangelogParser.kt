package org.wip.plugintoolkit.api.utils

import org.wip.plugintoolkit.api.Changelog
import org.wip.plugintoolkit.api.Release

object ChangelogParser {
    private val SEPARATOR = "-".repeat(100)

    fun parse(content: String): Changelog {
        val releases = mutableListOf<Release>()
        var currentDate = ""
        var currentVersion = ""
        var currentCategories = mutableMapOf<String, MutableList<String>>()
        var currentCategoryName = ""

        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed == SEPARATOR) {
                if (currentVersion.isNotEmpty()) {
                    releases.add(Release(currentVersion, currentDate, currentCategories))
                    currentDate = ""
                    currentVersion = ""
                    currentCategories = mutableMapOf()
                    currentCategoryName = ""
                }
                continue
            }

            if (trimmed.startsWith("Date:", ignoreCase = true)) {
                currentDate = trimmed.removePrefix("Date:").trim()
                continue
            }

            if (trimmed.startsWith("Version:", ignoreCase = true)) {
                currentVersion = trimmed.removePrefix("Version:").trim()
                continue
            }

            if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.endsWith(":")) {
                currentCategoryName = trimmed.removeSuffix(":")
                currentCategories[currentCategoryName] = mutableListOf()
            } else if (currentCategoryName.isNotEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                val item = trimmed.removePrefix("-").trim()
                if (item.isNotEmpty()) {
                    currentCategories.getOrPut(currentCategoryName) { mutableListOf() }.add(item)
                }
            }
        }

        if (currentVersion.isNotEmpty()) {
            releases.add(Release(currentVersion, currentDate, currentCategories))
        }

        return Changelog(releases)
    }
}
