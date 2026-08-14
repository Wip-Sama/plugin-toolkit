package org.wip.plugintoolkit.cli

import kotlinx.coroutines.flow.first
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.performStartup
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

sealed interface ToolkitCliCommand {
    data object Help : ToolkitCliCommand
    data object Version : ToolkitCliCommand
    data object Status : ToolkitCliCommand
    data object Plugins : ToolkitCliCommand
    data object Flows : ToolkitCliCommand
}

fun parseToolkitCliCommand(args: Array<String>): ToolkitCliCommand? = when (args.toList()) {
    listOf("--help"), listOf("-h"), listOf("help") -> ToolkitCliCommand.Help
    listOf("--version"), listOf("version") -> ToolkitCliCommand.Version
    listOf("status") -> ToolkitCliCommand.Status
    listOf("plugins"), listOf("plugins", "list") -> ToolkitCliCommand.Plugins
    listOf("flows"), listOf("flows", "list") -> ToolkitCliCommand.Flows
    else -> null
}

suspend fun runToolkitCli(
    command: ToolkitCliCommand,
    output: (String) -> Unit = ::println,
    error: (String) -> Unit = System.err::println
): Int {
    when (command) {
        ToolkitCliCommand.Help -> {
            output(CLI_HELP)
            return 0
        }
        ToolkitCliCommand.Version -> {
            output(AppConfig.VERSION)
            return 0
        }
        else -> Unit
    }

    return try {
        performStartup(emptyArray())
        val koin = getKoin()
        val registry = koin.get<PluginRegistry>()
        registry.isReady.first { it }

        when (command) {
            ToolkitCliCommand.Status -> {
                val plugins = registry.installedPlugins.value
                output("PluginToolkit ${AppConfig.VERSION}")
                output("Plugins: ${plugins.size} installed, ${plugins.count { it.isEnabled }} enabled")
            }
            ToolkitCliCommand.Plugins -> {
                val plugins = registry.installedPlugins.value
                if (plugins.isEmpty()) output("No plugins installed.")
                plugins.forEach { plugin ->
                    val state = when {
                        !plugin.isCompatible -> "incompatible"
                        !plugin.isEnabled -> "disabled"
                        plugin.isValidated -> "ready"
                        else -> "setup required"
                    }
                    output("${plugin.pkg}\t${plugin.version}\t$state")
                }
            }
            ToolkitCliCommand.Flows -> {
                val settingsDir = koin.get<SettingsPersistence>().getSettingsDir()
                val flowsDir = Path.of(settingsDir, "flows")
                val flows = if (Files.isDirectory(flowsDir)) {
                    Files.list(flowsDir).use { paths ->
                        paths.filter { it.isRegularFile() && it.extension == "json" }
                            .map { it.nameWithoutExtension }
                            .sorted()
                            .toList()
                    }
                } else emptyList()
                if (flows.isEmpty()) output("No flows saved.") else flows.forEach(output)
            }
            else -> Unit
        }
        stopKoin()
        0
    } catch (exception: Throwable) {
        error("CLI error: ${exception.message ?: exception::class.simpleName}")
        stopKoin()
        1
    }
}

private val CLI_HELP = """
    PluginToolkit ${AppConfig.VERSION}

    Usage: plugintoolkit <command>

    Commands:
      status          Show application and plugin status
      plugins list    List installed plugins and readiness
      flows list      List saved flows
      version         Print the application version
      help            Show this help
""".trimIndent()
