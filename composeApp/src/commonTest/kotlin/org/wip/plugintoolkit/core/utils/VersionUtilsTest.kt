package org.wip.plugintoolkit.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class VersionUtilsTest {

    @Test
    fun testCompare() {
        assertEquals(0, VersionUtils.compare("1.0.0", "1.0.0"))
        assertEquals(1, VersionUtils.compare("1.1.0", "1.0.0"))
        assertEquals(-1, VersionUtils.compare("1.0.0", "1.1.0"))
        assertEquals(1, VersionUtils.compare("1.0.1", "1.0.0"))
        assertEquals(1, VersionUtils.compare("2.0.0", "1.9.9"))
        assertEquals(0, VersionUtils.compare("v1.0.0", "1.0.0"))
        assertEquals(1, VersionUtils.compare("1.1", "1.0.5"))
    }

    @Test
    fun testIsAtLeast() {
        assertTrue(VersionUtils.isAtLeast("1.5.0", "1.4.0"))
        assertTrue(VersionUtils.isAtLeast("1.5.0", "1.5.0"))
        assertFalse(VersionUtils.isAtLeast("1.4.0", "1.5.0"))
    }

    @Test
    fun testIsAtMost() {
        assertTrue(VersionUtils.isAtMost("1.4.0", "1.5.0"))
        assertTrue(VersionUtils.isAtMost("1.5.0", "1.5.0"))
        assertFalse(VersionUtils.isAtMost("1.6.0", "1.5.0"))
    }

    @Test
    fun testIsWithinRange() {
        assertTrue(VersionUtils.isWithinRange("1.5.0", "1.0.0", "2.0.0"))
        assertTrue(VersionUtils.isWithinRange("1.0.0", "1.0.0", "2.0.0"))
        assertTrue(VersionUtils.isWithinRange("2.0.0", "1.0.0", "2.0.0"))
        assertFalse(VersionUtils.isWithinRange("0.9.0", "1.0.0", "2.0.0"))
        assertFalse(VersionUtils.isWithinRange("2.1.0", "1.0.0", "2.0.0"))
    }
}
