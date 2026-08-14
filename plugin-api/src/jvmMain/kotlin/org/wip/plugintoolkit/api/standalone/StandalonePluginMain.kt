package org.wip.plugintoolkit.api.standalone

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginModuleProvider
import java.util.ServiceLoader
import kotlin.system.exitProcess

/** Entry point embedded in standalone plugin JARs. */
fun main(args: Array<String>) {
    val exitCode = runStandalone(args, System.out::println, System.err::println)
    if (exitCode != 0) exitProcess(exitCode)
}

internal fun runStandalone(
    args: Array<String>,
    output: (String) -> Unit,
    error: (String) -> Unit,
    loadPlugins: () -> List<PluginEntry> = ::loadStandalonePlugins
): Int {
    when (args.firstOrNull()) {
        "--help", "-h" -> {
            output("Usage: java -jar <plugin>-standalone.jar [--info|--help]")
            return 0
        }
        null, "--info" -> Unit
        else -> {
            error("Unknown option '${args.first()}'. Use --help.")
            return 2
        }
    }

    val plugins = loadPlugins()
    if (plugins.isEmpty()) {
        error("No PluginEntry service was found in this JAR.")
        return 2
    }

    output(describeStandalonePlugins(plugins))
    return 0
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
