package org.wip.plugintoolkit.cli

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.core.DefaultSystemConfig
import org.wip.plugintoolkit.core.loomDispatcher
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

sealed interface ToolkitCliCommand {
    data object Help : ToolkitCliCommand
    data object Version : ToolkitCliCommand
    data object Status : ToolkitCliCommand
    data object Plugins : ToolkitCliCommand
    data object Flows : ToolkitCliCommand
}

sealed interface ToolkitCliInvocation {
    data object Desktop : ToolkitCliInvocation
    data class Command(val command: ToolkitCliCommand) : ToolkitCliInvocation
    data class Invalid(val arguments: List<String>) : ToolkitCliInvocation
}

fun parseToolkitCliCommand(args: Array<String>): ToolkitCliCommand? = when (args.toList()) {
    listOf("--help"), listOf("-h"), listOf("help") -> ToolkitCliCommand.Help
    listOf("--version"), listOf("version") -> ToolkitCliCommand.Version
    listOf("status") -> ToolkitCliCommand.Status
    listOf("plugins"), listOf("plugins", "list") -> ToolkitCliCommand.Plugins
    listOf("flows"), listOf("flows", "list") -> ToolkitCliCommand.Flows
    else -> null
}

fun parseToolkitCliInvocation(args: Array<String>): ToolkitCliInvocation {
    parseToolkitCliCommand(args)?.let { return ToolkitCliInvocation.Command(it) }
    if (args.isEmpty() || args.all { it == DefaultSystemConfig().STARTUP_FLAG_BACKGROUND || it.startsWith("-psn_") }) {
        return ToolkitCliInvocation.Desktop
    }
    return ToolkitCliInvocation.Invalid(args.toList())
}

internal data class ToolkitCliData(
    val plugins: List<InstalledPlugin> = emptyList(),
    val flowNames: List<String> = emptyList()
)

internal suspend fun runToolkitCli(
    command: ToolkitCliCommand,
    output: (String) -> Unit = ::println,
    error: (String) -> Unit = System.err::println,
    dataLoader: suspend (ToolkitCliCommand) -> ToolkitCliData = ::loadToolkitCliData
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
        // The CLI owns the process and emits only its data on stdout.
        Logger.setLogWriters()
        val data = withTimeout(CLI_STARTUP_TIMEOUT_MS) { dataLoader(command) }
        when (command) {
            ToolkitCliCommand.Status -> {
                output("PluginToolkit ${AppConfig.VERSION}")
                output("Plugins: ${data.plugins.size} installed, ${data.plugins.count { it.isEnabled }} enabled")
            }
            ToolkitCliCommand.Plugins -> {
                if (data.plugins.isEmpty()) output("No plugins installed.")
                data.plugins.forEach { plugin ->
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
                if (data.flowNames.isEmpty()) output("No flows saved.") else data.flowNames.sorted().forEach(output)
            }
            else -> Unit
        }
        0
    } catch (exception: Throwable) {
        error("CLI error: ${exception.message ?: exception::class.simpleName}")
        1
    }
}

private suspend fun loadToolkitCliData(command: ToolkitCliCommand): ToolkitCliData {
    val appConfig = DefaultSystemConfig()
    val persistence = JvmSettingsPersistence(appConfig)
    if (command == ToolkitCliCommand.Flows) {
        return ToolkitCliData(
            flowNames = FlowRepository.loadStoredFlows(persistence, appConfig).map { it.name }
        )
    }

    val scope = CoroutineScope(SupervisorJob() + loomDispatcher)
    return try {
        val settingsRepository = SettingsRepository(persistence, scope)
        settingsRepository.isLoaded.first { it }
        val registry = PluginRegistry(settingsRepository, scope, loomDispatcher, appConfig)
        registry.initialize()
        ToolkitCliData(plugins = registry.installedPlugins.value)
    } finally {
        scope.cancel()
    }
}

private const val CLI_STARTUP_TIMEOUT_MS = 15_000L

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
