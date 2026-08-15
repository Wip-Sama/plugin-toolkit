package org.wip.plugintoolkit.features.plugin.logic

import org.wip.plugintoolkit.api.utils.ChangelogParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogParserTest {
    private val separator = "-".repeat(100)

    @Test
    fun testParseWithSeparators() {
        val content = """
            Version: 1.0.0
            Date: 2026-05-05
            General:
              - Item 1
            
            ${separator}
            
            Version: 0.9.0
            Date: 2026-04-01
            Fixes:
              - Bug fix
        """.trimIndent()

        val versions = ChangelogParser.parse(content).releases
        assertEquals(2, versions.size)

        assertEquals("1.0.0", versions[0].version)
        assertEquals("2026-05-05", versions[0].date)

        assertEquals("0.9.0", versions[1].version)
        assertEquals("2026-04-01", versions[1].date)
    }

    @Test
    fun testParseWithoutSeparatorShouldOverwrite() {
        // Without the 100-dash separator, the second "Version:" just overwrites the first one in the same block.
        val content = """
            Version: 1.0.0
            Date: 2026-05-05
            
            Version: 0.9.0
            Date: 2026-04-01
        """.trimIndent()

        val versions = ChangelogParser.parse(content).releases
        // Only one version is produced (the last one found in the block)
        assertEquals(1, versions.size)
        assertEquals("0.9.0", versions[0].version)
        assertEquals("2026-04-01", versions[0].date)
    }

    @Test
    fun testParseMixedOrder() {
        val content = """
            Date: 2026-05-05
            Version: 1.0.0
            General:
              - Item 1
        """.trimIndent()

        val versions = ChangelogParser.parse(content).releases
        assertEquals(1, versions.size)
        assertEquals("1.0.0", versions[0].version)
        assertEquals("2026-05-05", versions[0].date)
    }

    @Test
    fun testIgnoreExtraNewlinesAndGarbageSeparators() {
        val content = """
            
            
            Version: 1.0.0
            
            
            Date: 2026-05-05
            
            ---
            
            General:
              - Item 1
            
            
        """.trimIndent()

        val versions = ChangelogParser.parse(content).releases
        assertEquals(1, versions.size)
        assertEquals("1.0.0", versions[0].version)
    }

    @Test
    fun testParseVersionName() {
        val content = """
            Version: 2.0.0
            VersionName: Major Redesign
            Date: 2026-06-01
            Features:
              - Added new UI
            
            ${separator}
            
            VersionName: Security Hotfix
            Version: 1.9.1
            Date: 2026-05-15
            Fixes:
              - Fixed CVE
            
            ${separator}
            
            Version: 1.9.0
            Date: 2026-05-01
            General:
              - Unnamed update
        """.trimIndent()

        val versions = ChangelogParser.parse(content).releases
        assertEquals(3, versions.size)

        assertEquals("2.0.0", versions[0].version)
        assertEquals("Major Redesign", versions[0].versionName)
        assertEquals("Major Redesign", versions[0].name)
        assertEquals("2026-06-01", versions[0].date)

        assertEquals("1.9.1", versions[1].version)
        assertEquals("Security Hotfix", versions[1].versionName)
        assertEquals("Security Hotfix", versions[1].name)
        assertEquals("2026-05-15", versions[1].date)

        assertEquals("1.9.0", versions[2].version)
        assertEquals(null, versions[2].versionName)
        assertEquals(null, versions[2].name)
        assertEquals("2026-05-01", versions[2].date)
    }

    @Test
    fun testParseVersionNameCaseInsensitive() {
        val content = """
            version: 1.0.0
            versionname: Cool Edition
            date: 2026-01-01
            General:
              - Initial
        """.trimIndent()

        val versions = ChangelogParser.parse(content).releases
        assertEquals(1, versions.size)
        assertEquals("1.0.0", versions[0].version)
        assertEquals("Cool Edition", versions[0].versionName)
    }
}
