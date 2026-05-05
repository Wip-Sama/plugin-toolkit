package org.wip.plugintoolkit.features.plugin.logic

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

        val versions = ChangelogParser.parse(content)
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

        val versions = ChangelogParser.parse(content)
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

        val versions = ChangelogParser.parse(content)
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

        val versions = ChangelogParser.parse(content)
        assertEquals(1, versions.size)
        assertEquals("1.0.0", versions[0].version)
    }
}
