package org.wip.plugintoolkit.api.utils

import org.wip.plugintoolkit.api.Changelog
import org.wip.plugintoolkit.api.Release

class ChangelogParser {
    companion object {
        fun parse(content: String): Changelog {
            val releases = mutableListOf<Release>()
            var currentDate = ""
            var currentVersion = ""
            var currentVersionName: String? = null
            var currentCategories = mutableMapOf<String, MutableList<String>>()
            var currentCategoryName = ""

            val lines = content.lines()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.all { it == '-' } && trimmed.length >= 50) {
                    if (currentVersion.isNotEmpty()) {
                        releases.add(Release(currentVersion, currentDate, currentCategories, currentVersionName))
                        currentDate = ""
                        currentVersion = ""
                        currentVersionName = null
                        currentCategories = mutableMapOf()
                        currentCategoryName = ""
                    }
                    continue
                }

                if (trimmed.startsWith("VersionName:", ignoreCase = true) ||
                    trimmed.startsWith("Version Name:", ignoreCase = true) ||
                    trimmed.startsWith("Version_Name:", ignoreCase = true)
                ) {
                    currentVersionName = trimmed.substringAfter(":", "").trim()
                    continue
                }

                if (trimmed.startsWith("Date:", ignoreCase = true)) {
                    currentDate = trimmed.substringAfter(":", "").trim()
                    continue
                }

                if (trimmed.startsWith("Version:", ignoreCase = true)) {
                    currentVersion = trimmed.substringAfter(":", "").trim()
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
                releases.add(Release(currentVersion, currentDate, currentCategories, currentVersionName))
            }

            return Changelog(releases)
        }
    }
}
