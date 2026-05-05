package org.wip.plugintoolkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.logging.FileLogWriter
import org.wip.plugintoolkit.core.notification.JvmNotificationService
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.PlatformPathUtils
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.features.navigation.viewmodel.AppViewModel
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginManagerViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.LogLevel
import org.wip.plugintoolkit.features.settings.model.WindowStartMode
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsSearchViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name
import plugintoolkit.composeapp.generated.resources.compose_multiplatform
import java.awt.Dimension


fun main(args: Array<String>) {
    FileKit.init(appId = "org.wip.plugintoolkit")

    // Check if we should start minimized
    val repository = SettingsRepository()
    val initialSettings = repository.loadSettings()

    // We need a late-init provider for settings because viewModel is created after startKoin
    // but viewModel needs NotificationService which needs settings.
    // However, since viewModel is created manually, we can just use a lambda that captures it later.
    var viewModelProvider: () -> SettingsViewModel? = { null }

    startKoin {
        modules(module {
            single { repository }
            single<NotificationService> { JvmNotificationService { viewModelProvider()!!.settings } }
            single { SettingsRegistry() }
            single { RepoManager(get()) }
            single { DialogService() }
            single { PluginManager(get(), get(), get()) }
            single {
                JobManager(
                    CoroutineScope(SupervisorJob() + Dispatchers.Default),
                    repository.loadSettings().jobs.maxConcurrentJobs
                )
            }
            single { PluginViewModel(get(), get(), get()) }
            factory { SettingsViewModel(get(), get()) }
            factory { NotificationViewModel(get()) }
            factory { SettingsSearchViewModel(get()) }
            factory { PluginRepoViewModel(get(), get(), get(), get(), get()) }
            factory { PluginManagerViewModel(get(), get(), get()) }
            factory { JobViewModel(get()) }
            factory { AppViewModel(get(), get()) }
            single { UpdateService() }
        })
    }

    val koin = getKoin()
    val viewModel = koin.get<SettingsViewModel>()
    koin.get<RepoManager>() // Trigger initialization and background refresh
    val pluginManager = koin.get<PluginManager>()

    viewModelProvider = { viewModel }

    // Initialize Logging with Kermit
    val logDirPath = "${PlatformPathUtils.getAppDataDir()}/${KeepTrack.LOGS_DIR_NAME}"
    val logDir = Path(logDirPath)

    Logger.setLogWriters(platformLogWriter(), FileLogWriter(logDir) { viewModel.settings.logging })

    // Sync logger severity with settings
    snapshotFlow { viewModel.settings.logging.level }.onEach { level ->
        val severity = when (level) {
            LogLevel.Verbose -> Severity.Verbose
            LogLevel.Debug -> Severity.Debug
            LogLevel.Info -> Severity.Info
            LogLevel.Warn -> Severity.Warn
            LogLevel.Error -> Severity.Error
            LogLevel.Assert -> Severity.Assert
        }
        Logger.setMinSeverity(severity)
    }.launchIn(CoroutineScope(Dispatchers.Default))

    Logger.i { "Application started. Logging initialized at: $logDir" }


    // Load enabled and validated plugins at startup
    val startupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val pluginsToLoad = pluginManager.installedPlugins.value.filter { it.isEnabled }
    Logger.i { "Startup: Found ${pluginsToLoad.size} enabled plugins to load/setup" }

    pluginsToLoad.forEach { plugin ->
        if (plugin.isValidated) {
            Logger.d { "Startup: Launching load for validated plugin ${plugin.pkg}" }
            startupScope.launch {
                val result = pluginManager.loadPlugin(plugin.pkg)
                if (result.isFailure) {
                    Logger.e { "Startup: Failed to load plugin ${plugin.pkg}: ${result.exceptionOrNull()?.message}" }
                }
            }
        } else {
            Logger.i { "Startup: Plugin ${plugin.pkg} is enabled but not validated, triggering background setup" }
            startupScope.launch {
                pluginManager.enqueueSetupJob(plugin.pkg)
            }
        }
    }

    // Determine initial window state based on settings and flags
    val startMinimizedOverride = args.contains(KeepTrack.STARTUP_FLAG_BACKGROUND)
    val startMode = if (startMinimizedOverride) WindowStartMode.Minimized else initialSettings.general.windowStartMode

    application {
        val trayState = rememberTrayState()
        var isVisible by remember { mutableStateOf(startMode != WindowStartMode.Minimized) }

        val windowState = rememberWindowState(
            placement = when (startMode) {
                WindowStartMode.Maximized -> WindowPlacement.Maximized
                WindowStartMode.Fullscreen -> WindowPlacement.Fullscreen
                else -> WindowPlacement.Floating
            },
            position = WindowPosition(Alignment.Center),
            size = DpSize(1280.dp, 800.dp)
        )

        Tray(
            state = trayState,
            icon = painterResource(Res.drawable.compose_multiplatform),
            tooltip = stringResource(Res.string.app_name),
            onAction = { isVisible = true },
            menu = {
                Item("Open", onClick = { isVisible = true })
                Separator()
                Item("Exit", onClick = { exitApplication() })
            }
        )

        if (isVisible) {
            Window(
                onCloseRequest = {
                    if (viewModel.settings.general.closeToTray) {
                        isVisible = false
                    } else {
                        exitApplication()
                    }
                },
                title = stringResource(Res.string.app_name),
                state = windowState
            ) {
                // Set minimum window size
                window.minimumSize = Dimension(1000, 600)
                val notificationService = getKoin().get<NotificationService>()

                LaunchedEffect(Unit) {
                    val trayState = trayState // from application scope
                    notificationService.events.collect { event ->
                        if (event is NotificationEvent.System) {
                            trayState.sendNotification(
                                Notification(
                                    title = event.record.title,
                                    message = event.record.message,
                                    type = when (event.record.type) {
                                        NotificationType.Info -> Notification.Type.Info
                                        NotificationType.Warning -> Notification.Type.Warning
                                        NotificationType.Error -> Notification.Type.Error
                                    }
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    App(viewModel = viewModel)
                }
            }
        }
    }
}
