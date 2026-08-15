package org.wip.plugintoolkit.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.core.DefaultSystemConfig
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence

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
    val knownCommandRoots = setOf("--help", "-h", "help", "--version", "version", "status", "plugins", "flows")
    return if (args.first() in knownCommandRoots) {
        ToolkitCliInvocation.Invalid(args.toList())
    } else {
        // Desktop launchers and future file associations may inject their own arguments.
        // Preserve the pre-CLI behavior unless the user clearly attempted a toolkit command.
        ToolkitCliInvocation.Desktop
    }
}

internal data class ToolkitCliData(
    val plugins: List<InstalledPlugin> = emptyList(),
    val flowNames: List<String> = emptyList()
)

internal suspend fun runToolkitCli(
    command: ToolkitCliCommand,
    output: (String) -> Unit = ::println,
    error: (String) -> Unit = System.err::println,
    dataLoader: suspend (ToolkitCliCommand) -> ToolkitCliData = { loadToolkitCliData(it) }
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
            ToolkitCliCommand.Help, ToolkitCliCommand.Version -> Unit
        }
        0
    } catch (exception: Throwable) {
        error("CLI error: ${exception.message ?: exception::class.simpleName}")
        1
    }
}

internal suspend fun loadToolkitCliData(
    command: ToolkitCliCommand,
    appConfig: SystemConfig = DefaultSystemConfig(),
    settingsDir: String? = null
): ToolkitCliData = withContext(Dispatchers.IO) {
    val persistence = JvmSettingsPersistence(appConfig, settingsDir)
    when (command) {
        ToolkitCliCommand.Flows -> ToolkitCliData(flowNames = loadFlowNamesReadOnly(persistence.getSettingsDir(), appConfig))
        ToolkitCliCommand.Status, ToolkitCliCommand.Plugins -> {
            val settings = persistence.load()
            val defaultFolder = "${persistence.getSettingsDir()}/${appConfig.PLUGINS_DIR_NAME}"
            val folders = (listOf(defaultFolder) + settings.extensions.pluginFolders).distinct()
            ToolkitCliData(plugins = folders.flatMap { loadPluginsReadOnly(it, appConfig) })
        }
        else -> ToolkitCliData()
    }
}

private val storageJson = Json { ignoreUnknownKeys = true }

private fun loadFlowNamesReadOnly(settingsDir: String, appConfig: SystemConfig): List<String> {
    val flows = mutableListOf<Flow>()
    val flowsDir = Path("$settingsDir/flows")
    if (SystemFileSystem.exists(flowsDir)) {
        SystemFileSystem.list(flowsDir)
            .filter { it.name.endsWith(".json") }
            .mapNotNullTo(flows) { file ->
                runCatching {
                    val content = SystemFileSystem.source(file).buffered().use { it.readString() }
                    storageJson.decodeFromString<Flow>(content)
                }.getOrNull()
            }
    }

    val legacyFile = Path("$settingsDir/${appConfig.FLOWS_FILE_NAME}")
    if (SystemFileSystem.exists(legacyFile)) {
        runCatching {
            val content = SystemFileSystem.source(legacyFile).buffered().use { it.readString() }
            if (content.isNotBlank()) storageJson.decodeFromString<List<Flow>>(content) else emptyList()
        }.getOrDefault(emptyList()).forEach(flows::add)
    }
    return flows.distinctBy { it.name }.map { it.name }
}

private fun loadPluginsReadOnly(folder: String, appConfig: SystemConfig): List<InstalledPlugin> {
    val registryFile = Path("${folder.replace('\\', '/').removeSuffix("/")}/${appConfig.INSTALLED_PLUGINS_FILE_NAME}")
    if (!SystemFileSystem.exists(registryFile)) return emptyList()
    val content = SystemFileSystem.source(registryFile).buffered().use { it.readString() }
    return storageJson.decodeFromString<List<InstalledPlugin>>(content)
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
