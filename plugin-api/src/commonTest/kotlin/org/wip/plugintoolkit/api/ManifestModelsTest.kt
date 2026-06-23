package org.wip.plugintoolkit.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManifestModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testFileAccessSerialization() {
        val fileAccess = FileAccess(readsFiles = true, writesFiles = false, isDestructive = true)
        val serialized = json.encodeToString(fileAccess)
        
        val deserialized = json.decodeFromString<FileAccess>(serialized)
        assertEquals(true, deserialized.readsFiles)
        assertEquals(false, deserialized.writesFiles)
        assertEquals(true, deserialized.isDestructive)
    }

    @Test
    fun testCapabilityFileAccessSerialization() {
        val capability = Capability(
            name = "test_cap",
            description = "A test capability",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            fileAccess = FileAccess(readsFiles = true, writesFiles = true, isDestructive = false)
        )
        val serialized = json.encodeToString(capability)
        
        val deserialized = json.decodeFromString<Capability>(serialized)
        assertEquals("test_cap", deserialized.name)
        assertNotNull(deserialized.fileAccess)
        assertEquals(true, deserialized.fileAccess?.readsFiles)
        assertEquals(true, deserialized.fileAccess?.writesFiles)
        assertEquals(false, deserialized.fileAccess?.isDestructive)
    }
    
    @Test
    fun testCapabilityWithoutFileAccessSerialization() {
        val capability = Capability(
            name = "test_cap_no_files",
            description = "A test capability without file access",
            returnType = DataType.Primitive(PrimitiveType.UNIT)
        )
        val serialized = json.encodeToString(capability)
        
        val deserialized = json.decodeFromString<Capability>(serialized)
        assertEquals("test_cap_no_files", deserialized.name)
        assertEquals(null, deserialized.fileAccess)
    }
}
