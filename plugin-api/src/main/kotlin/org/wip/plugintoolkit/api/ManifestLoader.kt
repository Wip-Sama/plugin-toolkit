package org.wip.plugintoolkit.api

import kotlinx.serialization.json.Json

object ManifestLoader {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    /**
     * Loads the manifest.json from the resources of the provided class.
     */
    fun loadFromResources(clazz: Class<*>): PluginManifest {
        val resourceStream = clazz.getResourceAsStream("/META-INF/manifest.json")
            ?: throw IllegalStateException("manifest.json not found in resources of ${clazz.name}")
        
        val content = resourceStream.bufferedReader().use { it.readText() }
        return json.decodeFromString<PluginManifest>(content)
    }
}
