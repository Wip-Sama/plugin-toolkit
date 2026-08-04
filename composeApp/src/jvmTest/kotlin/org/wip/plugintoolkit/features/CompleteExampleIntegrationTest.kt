package org.wip.plugintoolkit.features

import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.PluginManifest
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompleteExampleIntegrationTest {

    @Test
    fun testCompleteExampleManifestStructure() {
        val jarFile = File("../completeExample/build/libs/completeExample.jar")
        if (!jarFile.exists()) return

        val zip = ZipFile(jarFile)
        val entry = zip.getEntry("META-INF/manifest.json")
        assertNotNull(entry, "manifest.json entry should exist in JAR")

        val manifestString = zip.getInputStream(entry).reader().readText()
        val format = Json { ignoreUnknownKeys = true }
        val manifest = format.decodeFromString<PluginManifest>(manifestString)

        assertEquals("org.wip.complete", manifest.plugin.id)
        assertEquals("Complete Example Plugin", manifest.plugin.name)

        // Verify lifecycle handlers and changelog/migrations presence in manifest
        assertTrue(manifest.hasSetupHandler, "hasSetupHandler should be true")
        assertTrue(manifest.hasUpdateHandler, "hasUpdateHandler should be true")
        assertTrue(manifest.hasMigrations, "hasMigrations should be true")
        assertNotNull(manifest.changelog, "changelog should be present in manifest")

        // Verify capability with flow context
        val flowCap = manifest.capabilities.find { it.name == "capabilityWithFlowContext" }
        assertNotNull(flowCap, "capabilityWithFlowContext should be present in manifest")

        // Verify capability with pause support
        val pauseCap = manifest.capabilities.find { it.name == "capabilityWithPauseResume" }
        assertNotNull(pauseCap, "capabilityWithPauseResume should be present in manifest")
        assertTrue(pauseCap.isPausable, "capabilityWithPauseResume must support pause")

        // Verify capability with file defaults
        val fileDefCap = manifest.capabilities.find { it.name == "capabilityWithFileDefaults" }
        assertNotNull(fileDefCap, "capabilityWithFileDefaults should be present in manifest")

        // Verify capability with complex objects and semantic types
        val complexCap = manifest.capabilities.find { it.name == "capabilityWithComplexObjectsAndSemanticTypes" }
        assertNotNull(complexCap, "capabilityWithComplexObjectsAndSemanticTypes should be present in manifest")
    }
}
