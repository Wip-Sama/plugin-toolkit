package org.wip.plugintoolkit.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.core.DefaultSystemConfig
import org.wip.plugintoolkit.core.PortableSystemConfig
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.coroutineModule
import org.wip.plugintoolkit.core.logicModule
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationRecord
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import java.io.File

class NoOpNotificationService : NotificationService {
    override val history: StateFlow<List<NotificationRecord>> = MutableStateFlow(emptyList())
    override val events: SharedFlow<NotificationEvent> = MutableSharedFlow()

    override fun notify(
        title: String,
        message: String,
        type: NotificationType
    ) {
        println("[$type] $title: $message")
    }

    override fun toast(message: String, isNotification: Boolean) {
        println("[Toast] $message")
    }

    override fun toast(message: LocalizedString, isNotification: Boolean) {
        println("[Toast] $message")
    }

    override fun clearHistory() {}
    override fun removeHistoryItem(id: String) {}
}

class PluginToolkitCli : CliktCommand(
    name = "plugin-toolkit"
) {
    init {
        versionOption(CliAppConfig.VERSION)
    }

    private val listCapabilities by option(
        "--capabilities",
        help = "List all available capabilities from installed plugins"
    ).flag(default = false)

    private val listPlugins by option(
        "--plugins",
        help = "List all installed plugins and their validation status"
    ).flag(default = false)

    override fun run() = runBlocking {
        if (!listCapabilities && !listPlugins) {
            echo("Use --help to view available commands and options.")
            return@runBlocking
        }

        initKoin()
        val koin = getKoin()
        val pluginRegistry = koin.get<PluginRegistry>()
        val pluginManager = koin.get<PluginManager>()

        pluginRegistry.initialize()

        if (listPlugins) {
            val installed = pluginManager.installedPlugins.value
            echo("Installed Plugins (${installed.size}):")
            installed.forEach { p ->
                val status = if (p.isEnabled) "ENABLED" else "DISABLED"
                val validated = if (p.isValidated) "VALIDATED" else "UNVALIDATED"
                echo("  - ${p.pkg} (v${p.version}) [$status, $validated]")
            }
        }

        if (listCapabilities) {
            val installed = pluginManager.installedPlugins.value
            echo("Plugin Capabilities:")
            installed.forEach { p ->
                val pluginEntry = PluginLoader.getPluginById(p.pkg)
                val manifest = pluginEntry?.getManifest()?.getOrNull()
                if (manifest != null && manifest.capabilities.isNotEmpty()) {
                    echo("  Plugin: ${p.pkg}")
                    manifest.capabilities.forEach { cap ->
                        echo("    - ${cap.name}: ${cap.description ?: "No description"}")
                    }
                }
            }
        }

    }

    private fun initKoin() {
        val userDir = File(System.getProperty("user.dir"))
        val systemConfig: SystemConfig = if (File(userDir, ".portable").exists()) {
            PortableSystemConfig(userDir.absolutePath)
        } else {
            DefaultSystemConfig()
        }

        startKoin {
            modules(
                coroutineModule,
                module {
                    single<SystemConfig> { systemConfig }
                    single<SettingsPersistence> { JvmSettingsPersistence() }
                    single<NotificationService> { NoOpNotificationService() }
                },
                logicModule
            )
        }
    }
}

fun main(args: Array<String>) = PluginToolkitCli().main(args)
