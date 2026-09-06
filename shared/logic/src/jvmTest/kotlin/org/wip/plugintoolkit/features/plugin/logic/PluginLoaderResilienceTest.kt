package org.wip.plugintoolkit.features.plugin.logic

import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import org.koin.core.module.Module
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataProcessor
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PluginModuleProvider
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaultyPluginModuleProvider : PluginModuleProvider {
    override fun getKoinModule(settings: Map<String, JsonElement>): Module = module {
        single<PluginEntry> { FaultyPluginEntry() }
    }
}

class FaultyPluginEntry : PluginEntry {
    override fun getManifest(): Result<PluginManifest> {
        // Simulate an older plugin throwing NoSuchMethodError during static manifest initialization
        throw NoSuchMethodError(
            "void org.wip.plugintoolkit.api.PluginAction.<init>(java.lang.String, java.lang.String, java.lang.String, java.util.Map)"
        )
    }

    override fun getProcessor(): Result<DataProcessor> = Result.success(mockk())
    override fun setDebug(isDebug: Boolean) {}
}

class PluginLoaderResilienceTest {

    @Test
    fun testPluginLoaderRecoversWhenManifestInitThrowsNoSuchMethodError() {
        val jarFile = File.createTempFile("resilience-test-plugin", ".jar")
        jarFile.deleteOnExit()

        val manifestJson = """
        {
            "manifestVersion": "1.0",
            "plugin": {
                "id": "com.wip.faulty",
                "name": "Faulty Plugin",
                "version": "1.0.0",
                "description": "Tests resilience against NoSuchMethodError"
            },
            "requirements": {
                "minMemoryMb": 64,
                "minExecutionTimeMs": 100
            }
        }
        """.trimIndent()

        // Pack the test classes and manifest into a temporary JAR
        JarOutputStream(FileOutputStream(jarFile)).use { jos ->
            // META-INF/manifest.json
            jos.putNextEntry(JarEntry("META-INF/manifest.json"))
            jos.write(manifestJson.toByteArray())
            jos.closeEntry()

            // ServiceLoader SPI
            jos.putNextEntry(JarEntry("META-INF/services/org.wip.plugintoolkit.api.PluginModuleProvider"))
            jos.write(FaultyPluginModuleProvider::class.java.name.toByteArray())
            jos.closeEntry()

            // Pack the class files
            val classesToPack = listOf(
                FaultyPluginModuleProvider::class.java,
                FaultyPluginEntry::class.java
            )

            for (clazz in classesToPack) {
                val classResourcePath = clazz.name.replace('.', '/') + ".class"
                val classBytes = clazz.classLoader.getResourceAsStream(classResourcePath)?.readBytes()
                    ?: error("Could not find byte stream for ${clazz.name}")
                jos.putNextEntry(JarEntry(classResourcePath))
                jos.write(classBytes)
                jos.closeEntry()
            }
        }

        try {
            val result = PluginLoader.loadPlugin(jarFile.absolutePath, emptyMap())
            assertTrue(result.isSuccess, "PluginLoader should succeed via fallback when getManifest() throws NoSuchMethodError")

            val entry = result.getOrThrow()
            val manifestResult = entry.getManifest()
            assertTrue(manifestResult.isSuccess, "Entry getManifest() should return recovered manifest")
            assertEquals("com.wip.faulty", manifestResult.getOrThrow().plugin.id)
        } finally {
            PluginLoader.unloadPlugin(jarFile.absolutePath)
            jarFile.delete()
        }
    }
}
