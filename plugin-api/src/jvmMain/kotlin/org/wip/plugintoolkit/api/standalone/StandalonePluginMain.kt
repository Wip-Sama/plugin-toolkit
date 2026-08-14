package org.wip.plugintoolkit.api.standalone

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginModuleProvider
import java.util.ServiceLoader

/** Entry point embedded in standalone plugin JARs. */
fun main(args: Array<String>) {
    val plugins = loadStandalonePlugins()
    if (plugins.isEmpty()) {
        System.err.println("No PluginEntry service was found in this JAR.")
        return
    }

    when (args.firstOrNull()) {
        null, "--info" -> println(describeStandalonePlugins(plugins))
        "--help", "-h" -> println("Usage: java -jar <plugin>-standalone.jar [--info|--help]")
        else -> System.err.println("Unknown option '${args.first()}'. Use --help.")
    }
}

private fun loadStandalonePlugins(): List<PluginEntry> {
    val directEntries = ServiceLoader.load(PluginEntry::class.java).toList()
    if (directEntries.isNotEmpty()) return directEntries

    val providers = ServiceLoader.load(PluginModuleProvider::class.java).toList()
    if (providers.isEmpty()) return emptyList()

    stopKoin()
    val application = startKoin {
        modules(providers.map { it.getKoinModule(emptyMap()) })
    }
    return application.koin.getAll()
}

internal fun describeStandalonePlugins(plugins: List<PluginEntry>): String = plugins.joinToString("\n\n") { entry ->
    entry.getManifest().fold(
        onSuccess = { manifest ->
            buildString {
                appendLine("${manifest.plugin.name} ${manifest.plugin.version}")
                appendLine(manifest.plugin.description)
                append("Capabilities: ")
                append(manifest.capabilities.joinToString { it.name }.ifBlank { "none" })
            }
        },
        onFailure = { error -> "Invalid plugin manifest: ${error.message ?: error::class.simpleName}" }
    )
}
