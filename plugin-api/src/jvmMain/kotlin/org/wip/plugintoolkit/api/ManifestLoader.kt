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
            ?: clazz.classLoader?.getResourceAsStream("META-INF/manifest.json")
            ?: clazz.classLoader?.getResourceAsStream("/META-INF/manifest.json")
            ?: clazz.getResourceAsStream("/manifest.json")
            ?: clazz.classLoader?.getResourceAsStream("manifest.json")
            ?: throw IllegalStateException("manifest.json not found in resources of ${clazz.name}")

        val content = resourceStream.bufferedReader().use { it.readText() }
        return json.decodeFromString<PluginManifest>(content)
    }

    /**
     * Loads the manifest from raw JSON string with forward-compatible defaults.
     */
    fun loadFromString(jsonContent: String): PluginManifest {
        return json.decodeFromString<PluginManifest>(jsonContent)
    }
}
