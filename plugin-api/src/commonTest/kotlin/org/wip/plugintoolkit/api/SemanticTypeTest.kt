package org.wip.plugintoolkit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticTypeTest {

    @Test
    fun testParsingSingle() {
        // Simple name
        val t1 = parseSemanticType("color")
        assertNotNull(t1)
        assertNull(t1.namespace)
        assertEquals("color", t1.name)
        assertNull(t1.variant)
        assertEquals("color", t1.canonicalId)

        // Namespace and name
        val t2 = parseSemanticType("sys/color")
        assertNotNull(t2)
        assertEquals("sys", t2.namespace)
        assertEquals("color", t2.name)
        assertNull(t2.variant)
        assertEquals("sys/color", t2.canonicalId)

        // Namespace, name, and variant
        val t3 = parseSemanticType("sys/color:rgb")
        assertNotNull(t3)
        assertEquals("sys", t3.namespace)
        assertEquals("color", t3.name)
        assertEquals("rgb", t3.variant)
        assertEquals("sys/color:rgb", t3.canonicalId)

        // Case-insensitivity and trimming
        val t4 = parseSemanticType("  Sys/Color:Rgb  ")
        assertNotNull(t4)
        assertEquals("sys", t4.namespace)
        assertEquals("color", t4.name)
        assertEquals("rgb", t4.variant)
        assertEquals("sys/color:rgb", t4.canonicalId)

        // Standard parsing without MIME hacks (image/png -> namespace: image, name: png)
        val t5 = parseSemanticType("image/png")
        assertNotNull(t5)
        assertEquals("image", t5.namespace)
        assertEquals("png", t5.name)
        assertNull(t5.variant)
        assertEquals("image/png", t5.canonicalId)

        // Wildcard name
        val t6 = parseSemanticType("image/*")
        assertNotNull(t6)
        assertEquals("image", t6.namespace)
        assertEquals("*", t6.name)
        assertNull(t6.variant)
        assertEquals("image/*", t6.canonicalId)

        // Non-mime slash category behaves as namespace/name
        val t7 = parseSemanticType("custom/test")
        assertNotNull(t7)
        assertEquals("custom", t7.namespace)
        assertEquals("test", t7.name)
        assertNull(t7.variant)
        assertEquals("custom/test", t7.canonicalId)

        // Invalid cases
        assertNull(parseSemanticType(""))
        assertNull(parseSemanticType("   "))
    }

    @Test
    fun testParsingMultiple() {
        // Comma separated
        val list1 = parseSemanticTypes("sys/color:rgb, sys/color:rgba")
        assertEquals(2, list1.size)
        assertEquals("sys/color:rgb", list1[0].canonicalId)
        assertEquals("sys/color:rgba", list1[1].canonicalId)

        // Whitespace separated
        val list2 = parseSemanticTypes("sys/color:rgb   sys/color:rgba")
        assertEquals(2, list2.size)
        assertEquals("sys/color:rgb", list2[0].canonicalId)
        assertEquals("sys/color:rgba", list2[1].canonicalId)

        // Mixed comma and whitespace
        val list3 = parseSemanticTypes("sys/color:rgb,   sys/color:rgba")
        assertEquals(2, list3.size)
        assertEquals("sys/color:rgb", list3[0].canonicalId)
        assertEquals("sys/color:rgba", list3[1].canonicalId)

        // Empty / Null
        assertTrue(parseSemanticTypes(null).isEmpty())
        assertTrue(parseSemanticTypes("   ").isEmpty())
    }

    @Test
    fun testNormalization() {
        // NFKC normalization tests
        // Fullwidth small 'a' (\uFF41) normalizes to ascii 'a'
        val t1 = SemanticType("Sys", "Color", "a\uFF41")
        assertEquals("aa", t1.variant)

        // Kelvin sign (\u212A) normalizes to 'K' (lowercase 'k' because of lowercase step)
        val t2 = parseSemanticType("sys/color:\u212A")
        assertNotNull(t2)
        assertEquals("k", t2.variant)
    }

    @Test
    fun testCompatibility() {
        // Identity
        val s1 = parseSemanticTypes("sys/color:rgb")
        val t1 = parseSemanticTypes("sys/color:rgb")
        assertTrue(isSemanticTypeCompatible(s1, t1))

        // Generalization: source has variant, target does not
        val s2 = parseSemanticTypes("sys/color:rgb")
        val t2 = parseSemanticTypes("sys/color")
        assertTrue(isSemanticTypeCompatible(s2, t2))

        // Lenient Specialization: source does not have variant, target does
        val s3 = parseSemanticTypes("sys/color")
        val t3 = parseSemanticTypes("sys/color:rgb")
        assertTrue(isSemanticTypeCompatible(s3, t3, lenient = true))
        assertFalse(isSemanticTypeCompatible(s3, t3, lenient = false))

        // Wildcard
        val s4 = parseSemanticTypes("sys/color:rgb")
        val t4 = parseSemanticTypes("sys/color:*")
        assertTrue(isSemanticTypeCompatible(s4, t4))

        // Mismatches
        val s5 = parseSemanticTypes("sys/color")
        val t5 = parseSemanticTypes("sys/image")
        assertFalse(isSemanticTypeCompatible(s5, t5))

        val s6 = parseSemanticTypes("wip/color")
        val t6 = parseSemanticTypes("sys/color")
        assertFalse(isSemanticTypeCompatible(s6, t6))

        // Empty / Null lists are universally compatible
        assertTrue(isSemanticTypeCompatible(emptyList(), t1))
        assertTrue(isSemanticTypeCompatible(s1, emptyList()))
        assertTrue(isSemanticTypeCompatible(emptyList(), emptyList()))

        // Multi-valued lists
        val multiSource = parseSemanticTypes("sys/color, sys/file")
        val multiTarget = parseSemanticTypes("sys/image, sys/color:rgba")
        // sys/color matches sys/color:rgba (under lenient mode, since general matches specialized)
        assertTrue(isSemanticTypeCompatible(multiSource, multiTarget, lenient = true))
        assertFalse(isSemanticTypeCompatible(multiSource, multiTarget, lenient = false))

        val multiTargetStrict = parseSemanticTypes("sys/image, sys/color:rgb")
        assertTrue(isSemanticTypeCompatible(multiSource, multiTargetStrict, lenient = true))
        assertFalse(isSemanticTypeCompatible(multiSource, multiTargetStrict, lenient = false))
    }

    @Test
    fun testCommonSemanticTypes() {
        assertEquals("path", CommonSemanticTypes.PATH.canonicalId)
        assertEquals("path/file", CommonSemanticTypes.PATH_FILE.canonicalId)
        assertEquals("path/folder", CommonSemanticTypes.PATH_FOLDER.canonicalId)
        assertEquals("image", CommonSemanticTypes.IMAGE.canonicalId)
        assertEquals("image/png", CommonSemanticTypes.IMAGE_PNG.canonicalId)
        assertEquals("image/jpeg", CommonSemanticTypes.IMAGE_JPEG.canonicalId)
        assertEquals("text", CommonSemanticTypes.TEXT.canonicalId)
        assertEquals("text/plain", CommonSemanticTypes.TEXT_PLAIN.canonicalId)
        assertEquals("text/markdown", CommonSemanticTypes.TEXT_MARKDOWN.canonicalId)
        assertEquals("text/html", CommonSemanticTypes.TEXT_HTML.canonicalId)
        assertEquals("json", CommonSemanticTypes.JSON.canonicalId)
        assertEquals("xml", CommonSemanticTypes.XML.canonicalId)
    }
}
