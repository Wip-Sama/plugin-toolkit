package org.wip.plugintoolkit.api.standalone

import org.wip.plugintoolkit.api.ManifestLoader
import org.wip.plugintoolkit.api.PluginManifest
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
    loadManifest: () -> Result<PluginManifest> = ::loadStandaloneManifest
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

    val manifest = loadManifest().getOrElse { failure ->
        error("Plugin manifest could not be loaded: ${failure.message ?: failure::class.simpleName}")
        return 2
    }

    output(describeStandaloneManifest(manifest))
    return 0
}

private fun loadStandaloneManifest(): Result<PluginManifest> = runCatching {
    // Inspection must never instantiate third-party plugin code or synthesize missing settings.
    ManifestLoader.loadFromResources(PluginManifest::class.java)
}

internal fun describeStandaloneManifest(manifest: PluginManifest): String = buildString {
    appendLine("${manifest.plugin.name} ${manifest.plugin.version}")
    appendLine(manifest.plugin.description)
    append("Capabilities: ")
    append(manifest.capabilities.joinToString { it.name }.ifBlank { "none" })
}
