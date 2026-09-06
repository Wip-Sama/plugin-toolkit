package org.wip.plugintoolkit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BinaryCompatibilityTest {

    @Test
    fun testPluginActionLegacyFourArgConstructorExistsAndSetsDefaults() {
        // cleaner-1.1.0 and other pre-existing plugins call:
        // PluginAction.<init>(String, String, String, Map)
        val constructor = PluginAction::class.java.getConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            Map::class.java
        )
        assertNotNull(constructor, "4-arg constructor for PluginAction must exist in JVM bytecode")

        val action = constructor.newInstance("Clean Cache", "Removes temporary files", "cleanCache", null)
        assertEquals("Clean Cache", action.name)
        assertEquals("Removes temporary files", action.description)
        assertEquals("cleanCache", action.functionName)
        assertEquals(null, action.parameters)
        assertTrue(action.showToast, "showToast should default to true")
        assertEquals(null, action.toastMessage)
    }

    @Test
    fun testPluginActionThreeArgConstructorExists() {
        val constructor = PluginAction::class.java.getConstructor(
            String::class.java,
            String::class.java,
            String::class.java
        )
        assertNotNull(constructor, "3-arg constructor for PluginAction must exist in JVM bytecode")

        val action = constructor.newInstance("Action", "Desc", "exec")
        assertEquals("Action", action.name)
        assertEquals(null, action.parameters)
        assertTrue(action.showToast)
        assertEquals(null, action.toastMessage)
    }

    @Test
    fun testReleaseLegacyThreeArgConstructorExists() {
        val constructor = Release::class.java.getConstructor(
            String::class.java,
            String::class.java,
            Map::class.java
        )
        assertNotNull(constructor, "3-arg constructor for Release must exist in JVM bytecode")

        val release = constructor.newInstance("1.0.0", "2026-01-01", emptyMap<String, List<String>>())
        assertEquals("1.0.0", release.version)
        assertEquals(null, release.versionName)
    }

    @Test
    fun testPluginInfoLegacyFourArgConstructorExists() {
        val constructor = PluginInfo::class.java.getConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        assertNotNull(constructor, "4-arg constructor for PluginInfo must exist in JVM bytecode")

        val info = constructor.newInstance("com.test", "Test Plugin", "1.0.0", "Description")
        assertEquals("com.test", info.id)
        assertTrue(info.supportedOs.isEmpty())
    }

    @Test
    fun testRequirementsLegacyTwoArgConstructorExists() {
        val constructor = Requirements::class.java.getConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        assertNotNull(constructor, "2-arg constructor for Requirements must exist in JVM bytecode")

        val reqs = constructor.newInstance(256, 1000)
        assertEquals(256, reqs.minMemoryMb)
        assertEquals(1000, reqs.minExecutionTimeMs)
        assertEquals(null, reqs.targetAppVersion)
    }

    @Test
    fun testCapabilityThreeArgSecondaryConstructorExists() {
        val constructor = Capability::class.java.getConstructor(
            String::class.java,
            String::class.java,
            DataType::class.java
        )
        assertNotNull(constructor, "3-arg constructor for Capability must exist in JVM bytecode")

        val cap = constructor.newInstance("cap", "desc", DataType.Primitive(PrimitiveType.STRING))
        assertEquals("cap", cap.name)
        assertEquals(null, cap.parameters)
        assertEquals(CapabilityContext.ANY, cap.context)
    }

    @Test
    fun testParameterMetadataTwoArgConstructorExists() {
        val constructor = ParameterMetadata::class.java.getConstructor(
            String::class.java,
            DataType::class.java
        )
        assertNotNull(constructor, "2-arg constructor for ParameterMetadata must exist in JVM bytecode")

        val param = constructor.newInstance("input file", DataType.Primitive(PrimitiveType.STRING))
        assertEquals("input file", param.description)
        assertEquals(false, param.required)
        assertEquals(null, param.defaultValue)
    }

    @Test
    fun testSettingMetadataTwoArgConstructorExists() {
        val constructor = SettingMetadata::class.java.getConstructor(
            String::class.java,
            DataType::class.java
        )
        assertNotNull(constructor, "2-arg constructor for SettingMetadata must exist in JVM bytecode")

        val setting = constructor.newInstance("API Key", DataType.Primitive(PrimitiveType.STRING))
        assertEquals("API Key", setting.description)
        assertEquals(false, setting.required)
        assertEquals(false, setting.secret)
    }

    @Test
    fun testManifestLoaderBackwardCompatibilityWithMissingFields() {
        val jsonWithoutToast = """
        {
            "manifestVersion": "1.0",
            "plugin": {
                "id": "com.wip.cleaner",
                "name": "Cleaner",
                "version": "1.1.0",
                "description": "Clean temporary files"
            },
            "requirements": {
                "minMemoryMb": 128,
                "minExecutionTimeMs": 500
            },
            "actions": [
                {
                    "name": "Clean Cache",
                    "description": "Cleans the cache folder",
                    "functionName": "cleanCache"
                }
            ]
        }
        """.trimIndent()

        val manifest = ManifestLoader.loadFromString(jsonWithoutToast)
        assertEquals(1, manifest.actions.size)
        val action = manifest.actions.first()
        assertEquals("Clean Cache", action.name)
        assertTrue(action.showToast, "showToast should default to true when missing in JSON")
        assertEquals(null, action.toastMessage)
    }
}
