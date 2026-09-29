package org.wip.plugintoolkit.core.utils
 
import org.wip.plugintoolkit.api.CommonSemanticTypes
import org.wip.plugintoolkit.api.SemanticType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticRegistryTest {

    private val registry: SemanticRegistry = DefaultSemanticRegistry()

    @Test
    fun testPathFileMapsToFileCategory() {
        val category = registry.getCategory(listOf(CommonSemanticTypes.PATH_FILE))
        assertEquals(SemanticCategory.FILE, category)
    }

    @Test
    fun testPathFolderMapsToPathCategory() {
        val category = registry.getCategory(listOf(CommonSemanticTypes.PATH_FOLDER))
        assertEquals(SemanticCategory.PATH, category)
    }

    @Test
    fun testGenericPathMapsToPathCategory() {
        val category = registry.getCategory(listOf(CommonSemanticTypes.PATH))
        assertEquals(SemanticCategory.PATH, category)
    }

    @Test
    fun testGenericFileMapsToFileCategory() {
        val category = registry.getCategory(listOf(SemanticType(null, "file", null)))
        assertEquals(SemanticCategory.FILE, category)
    }

    @Test
    fun testPathFileWithVariantExtractsExtension() {
        val type = SemanticType("path", "file", "txt")
        val extensions = registry.getAllowedExtensions(listOf(type))
        assertEquals(listOf("txt"), extensions)
    }
}
