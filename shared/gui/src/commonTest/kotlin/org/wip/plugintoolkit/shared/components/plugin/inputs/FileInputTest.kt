package org.wip.plugintoolkit.shared.components.plugin.inputs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticType
import org.wip.plugintoolkit.core.utils.DefaultSemanticRegistry

class FileInputTest {

    private val semanticRegistry = DefaultSemanticRegistry()

    @Test
    fun testComputeAllowedExtensionsWithSemanticTypesOnly() {
        val types = listOfNotNull(parseSemanticType("path/file:json"))
        val allowed = computeAllowedExtensions(types, null, semanticRegistry)
        assertEquals(listOf("json"), allowed)
    }

    @Test
    fun testComputeAllowedExtensionsWithCustomExtensions() {
        val types = listOfNotNull(parseSemanticType("path/file:json"))
        val custom = listOf(".txt", " CSV ", "md")
        val allowed = computeAllowedExtensions(types, custom, semanticRegistry)
        assertEquals(listOf("txt", "csv", "md"), allowed)
    }

    @Test
    fun testComputeAllowedExtensionsWithNegation() {
        val types = listOfNotNull(parseSemanticType("image/png"), parseSemanticType("image/jpeg"))
        val custom = listOf("!png")
        val allowed = computeAllowedExtensions(types, custom, semanticRegistry)
        assertEquals(listOf("jpeg"), allowed)
    }

    @Test
    fun testComputeAllowedExtensionsWithCustomAndNegation() {
        val types = emptyList<SemanticType>()
        val custom = listOf("txt", "csv", "!csv")
        val allowed = computeAllowedExtensions(types, custom, semanticRegistry)
        assertEquals(listOf("txt"), allowed)
    }

    @Test
    fun testIsExtensionValidWithAllowedList() {
        val allowed = listOf("txt", "json")
        assertTrue(isExtensionValid("file.txt", allowed))
        assertTrue(isExtensionValid("C:\\docs\\MyFile.JSON", allowed))
        assertTrue(isExtensionValid("/var/log/app.Txt", allowed))
        assertFalse(isExtensionValid("file.csv", allowed))
        assertFalse(isExtensionValid("file_without_ext", allowed))
        assertTrue(isExtensionValid("", allowed))
        assertTrue(isExtensionValid("   ", allowed))
    }

    @Test
    fun testIsExtensionValidWithNegatedList() {
        val negated = listOf("exe", "bat")
        assertFalse(isExtensionValid("malware.exe", emptyList(), negated))
        assertFalse(isExtensionValid("script.BAT", emptyList(), negated))
        assertTrue(isExtensionValid("safe.txt", emptyList(), negated))
        assertTrue(isExtensionValid("no_extension", emptyList(), negated))
    }

    @Test
    fun testAppendPickedValue() {
        // Scalar value: replaces completely
        assertEquals("/path/to/b.txt", appendPickedValue("/path/to/a.txt", "/path/to/b.txt", isArray = false))

        // Array value: appends if not present
        assertEquals("/path/to/a.txt, /path/to/b.txt", appendPickedValue("/path/to/a.txt", "/path/to/b.txt", isArray = true))

        // Array value: does not duplicate
        assertEquals("/path/to/a.txt", appendPickedValue("/path/to/a.txt", "/path/to/a.txt", isArray = true))

        // Array value: handles empty initial
        assertEquals("/path/to/a.txt", appendPickedValue("", "/path/to/a.txt", isArray = true))
    }

    @Test
    fun testGetFileName() {
        assertEquals("test.txt", getFileName("/home/user/test.txt"))
        assertEquals("data.json", getFileName("C:\\Users\\user\\data.json"))
        assertEquals("test.txt, data.json", getFileNames("/home/user/test.txt, C:\\Users\\user\\data.json", isArray = true))
    }
}
