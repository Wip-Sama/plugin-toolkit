package org.wip.plugintoolkit.features.settings

import org.wip.plugintoolkit.features.settings.ui.MEMORY_TIERS
import org.wip.plugintoolkit.features.settings.ui.MaxMemoryLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxMemoryLogicTest {

    @Test
    fun testMemoryTiersContainRequiredValues() {
        assertTrue(MEMORY_TIERS.contains(1024), "Tiers must contain minimum 1024 MB (1 GB)")
        assertTrue(MEMORY_TIERS.contains(1536), "Tiers should contain 1.5 GB (1536 MB)")
        assertTrue(MEMORY_TIERS.contains(2048), "Tiers must contain default 2048 MB (2 GB)")
        assertTrue(MEMORY_TIERS.contains(3072), "Tiers should contain 3 GB (3072 MB)")
        assertTrue(MEMORY_TIERS.contains(4096), "Tiers should contain 4 GB (4096 MB)")
        assertTrue(MEMORY_TIERS.contains(8192), "Tiers should contain 8 GB (8192 MB)")
        assertTrue(MEMORY_TIERS.contains(16384), "Tiers should contain 16 GB (16384 MB)")
        assertTrue(MEMORY_TIERS.contains(32768), "Tiers should contain 32 GB (32768 MB)")
        assertTrue(MEMORY_TIERS.contains(65536), "Tiers should contain 64 GB (65536 MB)")

        // Verify sorted
        assertEquals(MEMORY_TIERS.sorted(), MEMORY_TIERS, "Tiers must be in ascending order")
    }

    @Test
    fun testParseAndValidateMemoryMbMode() {
        // Valid inputs >= 1024
        assertEquals(1024, MaxMemoryLogic.parseAndValidateMemory("1024", isGb = false))
        assertEquals(2048, MaxMemoryLogic.parseAndValidateMemory(" 2048 ", isGb = false))
        assertEquals(3500, MaxMemoryLogic.parseAndValidateMemory("3500", isGb = false))
        assertEquals(16384, MaxMemoryLogic.parseAndValidateMemory("16384", isGb = false))

        // Invalid inputs < 1024 (minimum 1GB)
        assertNull(MaxMemoryLogic.parseAndValidateMemory("1023", isGb = false))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("512", isGb = false))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("0", isGb = false))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("-1024", isGb = false))

        // Malformed inputs
        assertNull(MaxMemoryLogic.parseAndValidateMemory("", isGb = false))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("   ", isGb = false))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("abc", isGb = false))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("2048.5", isGb = false))
    }

    @Test
    fun testParseAndValidateMemoryGbMode() {
        // Valid inputs >= 1.0 GB
        assertEquals(1024, MaxMemoryLogic.parseAndValidateMemory("1", isGb = true))
        assertEquals(1024, MaxMemoryLogic.parseAndValidateMemory("1.0", isGb = true))
        assertEquals(1536, MaxMemoryLogic.parseAndValidateMemory("1.5", isGb = true))
        assertEquals(1536, MaxMemoryLogic.parseAndValidateMemory("1,5", isGb = true), "Comma decimal separator should be supported")
        assertEquals(2048, MaxMemoryLogic.parseAndValidateMemory("2", isGb = true))
        assertEquals(3072, MaxMemoryLogic.parseAndValidateMemory("3", isGb = true))
        assertEquals(4096, MaxMemoryLogic.parseAndValidateMemory(" 4.0 ", isGb = true))

        // Invalid inputs < 1.0 GB
        assertNull(MaxMemoryLogic.parseAndValidateMemory("0.9", isGb = true))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("0.5", isGb = true))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("0", isGb = true))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("-1", isGb = true))

        // Malformed inputs
        assertNull(MaxMemoryLogic.parseAndValidateMemory("", isGb = true))
        assertNull(MaxMemoryLogic.parseAndValidateMemory("invalid", isGb = true))
    }

    @Test
    fun testFormatMemoryOption() {
        assertEquals("1 GB", MaxMemoryLogic.formatMemoryOption(1024))
        assertEquals("1.5 GB", MaxMemoryLogic.formatMemoryOption(1536))
        assertEquals("2 GB", MaxMemoryLogic.formatMemoryOption(2048))
        assertEquals("4 GB", MaxMemoryLogic.formatMemoryOption(4096))
        assertEquals("3500 MB", MaxMemoryLogic.formatMemoryOption(3500))
    }
}
