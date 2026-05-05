package org.wip.plugintoolkit

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun testVersionIsSet() {
        val version = AppConfig.VERSION
        assertNotNull(version, "Version should not be null")
        assertTrue(version.isNotEmpty(), "Version should not be empty")

        // Basic semver check (e.g. 1.0.0)
        val semverRegex = Regex("""\d+\.\d+\.\d+.*""")
        assertTrue(version.matches(semverRegex), "Version '$version' should follow semver format")
    }
}
