package com.wip.plugin.processor

import com.wip.plugin.api.Changelog
import com.wip.plugin.api.Release

object ChangelogParser {
    fun parse(content: String): Changelog {
        val releases = mutableListOf<Release>()
        var currentVersion: String? = null
        var currentDate: String? = null
        var currentCategories = mutableMapOf<String, MutableList<String>>()
        var currentCategoryName: String? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.all { it == '-' }) return@forEach

            when {
                trimmed.startsWith("Version:", ignoreCase = true) -> {
                    if (currentVersion != null) {
                        releases.add(Release(currentVersion, currentDate ?: "", currentCategories.mapValues { it.value.toList() }))
                        currentCategories = mutableMapOf()
                        currentCategoryName = null
                    }
                    currentVersion = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("Date:", ignoreCase = true) -> {
                    currentDate = trimmed.substringAfter(":").trim()
                }
                !line.startsWith(" ") && trimmed.endsWith(":") -> {
                    val catName = trimmed.removeSuffix(":")
                    if (catName.equals("Version", ignoreCase = true) || catName.equals("Date", ignoreCase = true)) return@forEach
                    currentCategoryName = catName
                    currentCategories[currentCategoryName] = mutableListOf()
                }
                line.startsWith(" ") && trimmed.startsWith("-") -> {
                    val item = trimmed.removePrefix("-").trim()
                    currentCategoryName?.let { currentCategories[it]?.add(item) }
                }
            }
        }

        if (currentVersion != null) {
            releases.add(Release(currentVersion, currentDate ?: "", currentCategories.mapValues { it.value.toList() }))
        }

        return Changelog(releases)
    }
}
