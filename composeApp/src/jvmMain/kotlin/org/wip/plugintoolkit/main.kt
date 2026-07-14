package org.wip.plugintoolkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import io.github.vinceglb.filekit.FileKit
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.core.DefaultSystemConfig
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.coroutineModule
import org.wip.plugintoolkit.core.utils.DefaultSemanticRegistry
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.core.logging.FileLogWriter
import org.wip.plugintoolkit.core.notification.JvmNotificationService
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.PlatformLocalization
import org.wip.plugintoolkit.core.utils.PlatformPathUtils
import org.wip.plugintoolkit.core.utils.RealFileSystem
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.SystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.features.navigation.viewmodel.AppViewModel
import org.wip.plugintoolkit.features.plugin.logic.PluginFolderManager
import org.wip.plugintoolkit.features.plugin.logic.PluginInstaller
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLockProvider
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginScanner
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginManagerViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel
import org.wip.plugintoolkit.features.settings.definitions.appearanceDefinitions
import org.wip.plugintoolkit.features.settings.definitions.jobDefinitions
import org.wip.plugintoolkit.features.settings.definitions.loggingDefinitions
import org.wip.plugintoolkit.features.settings.definitions.notificationDefinitions
import org.wip.plugintoolkit.features.settings.definitions.pluginDefinitions
import org.wip.plugintoolkit.features.settings.definitions.systemDefinitions
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.LogLevel
import org.wip.plugintoolkit.features.settings.model.WindowStartMode
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.build
import org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsSearchViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_logo
import plugintoolkit.composeapp.generated.resources.app_name
import org.wip.plugintoolkit.ui.splash.showSplashWindow
import java.awt.Dimension
import javax.swing.JOptionPane
import javax.swing.JOptionPane.showMessageDialog
import javax.swing.JWindow
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    val splashWindow = try {
        showSplashWindow()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
    try {
        // Run startup on an IO thread while the AWT EDT freely animates the splash screen
        val (viewModel, mode) = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { 
            performStartup(args) { text ->
                splashWindow?.updateText(text)
            }
        }
        runMain(args, splashWindow?.window, viewModel, mode)
    } catch (e: Throwable) {
        e.printStackTrace()
        showMessageDialog(
            null,
            "Error during startup:\n${e.message}\n\nCheck logs for details.",
            "Startup Error",
            JOptionPane.ERROR_MESSAGE
        )
        System.exit(1)
    }
}

suspend fun performStartup(args: Array<String>, updateStatus: (String) -> Unit = {}): Pair<SettingsViewModel, WindowStartMode> {
    updateStatus("Loading configuration...")
    // Determine initial window state based on persistence directly
    val persistence = JvmSettingsPersistence()
    val initialSettings = persistence.load() // This is now a suspend call, no runBlocking

    // We need a late-init provider for settings because viewModel is created after startKoin
    // but viewModel needs NotificationService which needs settings.
    var viewModelProvider: () -> SettingsViewModel? = { null }

    updateStatus("Initializing Dependency Injection...")
    startKoin {
        modules(coroutineModule, module {
            single<SystemConfig> { DefaultSystemConfig() }
            single<SemanticRegistry> { DefaultSemanticRegistry() }
            single<SettingsPersistence> { JvmSettingsPersistence() }
            single { SettingsRepository(get(), get(named("LoomScope"))) }
            single<NotificationService> {
                val repository = get<SettingsRepository>()
                JvmNotificationService(get(named("LoomScope"))) {
                    viewModelProvider()?.settings?.value ?: repository.settings.value
                }
            }
            single<SettingsRegistry> {
                val settingsViewModel: SettingsViewModel = get()
                val notificationViewModel: NotificationViewModel = get()

                SettingsRegistry.build {
                    appearanceDefinitions()
                    systemDefinitions(settingsViewModel)
                    loggingDefinitions(settingsViewModel)
                    jobDefinitions()
                    notificationDefinitions(notificationViewModel)
                    pluginDefinitions()
                }
            }
            single<FileSystem> { RealFileSystem() }
            single {
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            }
            single {
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(get<Json>())
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 60000
                        connectTimeoutMillis = 15000
                        socketTimeoutMillis = 30000
                    }
                }
            }
            single { RepoManager(get(), get(), get(), get(named("LoomScope"))) }
            single { DialogService() }
            single { PluginLockProvider() }
            single { PluginRegistry(get(), get(named("AppScope")), get(named("LoomDispatcher")), get()) }
            single { PluginLifecycleManager(get(), get(), get(), get()) }
            single { PluginLifecycleCoordinator(get(), get(), get(), get(named("AppScope"))) }
            single { PluginFolderManager(get(), get(), get()) }
            single { PluginInstaller(get(), get(), get(), get(), get(), get(), get()) }
            single { PluginScanner(get(), get()) }
            single { PluginManager(get(), get(), get(), get(), get(), get(), get(), get(named("LoomScope"))) }
            single {
                JobManager(
                    get(named("LoomScope")),
                    get()
                )
            }
            single { PluginViewModel(get(), get(), get()) }
            single { SettingsViewModel(get(), get(), get(), get()) }
            single { FlowRepository(get(), get(), get(named("LoomScope")), get()) }
            single { FlowViewModel(get(), getOrNull(), getOrNull()) }
            single { ActiveFlowEditorTracker() }
            factory { (flowName: String) ->
                FlowEditorViewModel(
                    flowName,
                    get(),
                    getOrNull(),
                    getOrNull(),
                    getOrNull(),
                    getOrNull(),
                    getOrNull()
                )
            }
            factory { NotificationViewModel(get()) }
            factory { SettingsSearchViewModel(get()) }
            factory { PluginRepoViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
            factory { PluginManagerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
            factory { (pkg: String) -> PluginSettingsViewModel(pkg, get(), get()) }
            factory { JobViewModel(get()) }
            factory { AppViewModel(get(), get()) }
            single<SystemNodeExecutorRegistry> { DefaultSystemNodeExecutorRegistry(get()) }
            single { UpdateService(get()) }
        })
    }

    val koin = getKoin()
    val viewModel = koin.get<SettingsViewModel>()
    val registry = koin.get<PluginRegistry>()
    val pluginManager = koin.get<PluginManager>()
    val appScope = koin.get<CoroutineScope>(named("AppScope"))
    val settingsRepository = koin.get<SettingsRepository>()
    val updateService = koin.get<UpdateService>()
    val appConfig = koin.get<SystemConfig>()

    updateStatus("Cleaning up updates...")
    // Cleanup old updates on startup
    updateService.cleanupOldUpdates(settingsRepository.getSettingsDir())

    updateStatus("Initializing plugins...")
    // Initialize registry and subsequently load plugins
    appScope.launch {
        try {
            registry.initialize()
        } catch (e: Throwable) {
            Logger.e(e) { "Startup: Failed to initialize PluginRegistry" }
        }

        val pluginsToLoad = pluginManager.installedPlugins.value.filter { it.isEnabled }
        Logger.i { "Startup: Found ${pluginsToLoad.size} enabled plugins to load/setup" }

        pluginsToLoad.forEach { plugin ->
            if (plugin.isValidated) {
                Logger.d { "Startup: Launching load for validated plugin ${plugin.pkg}" }
                launch {
                    try {
                        val result = pluginManager.loadPlugin(plugin.pkg)
                        if (result.isFailure) {
                            Logger.e { "Startup: Failed to load plugin ${plugin.pkg}: ${result.exceptionOrNull()?.message}" }
                        }
                    } catch (e: Throwable) {
                        Logger.e(e) { "Startup: Unexpected error while loading plugin ${plugin.pkg}" }
                    }
                }
            } else {
                Logger.i { "Startup: Plugin ${plugin.pkg} is enabled but not validated, triggering background setup" }
                launch {
                    try {
                        pluginManager.enqueueSetupJob(plugin.pkg)
                    } catch (e: Throwable) {
                        Logger.e(e) { "Startup: Unexpected error while enqueuing setup for plugin ${plugin.pkg}" }
                    }
                }
            }
        }
    }

    updateStatus("Refreshing repositories...")
    koin.get<RepoManager>() // Trigger initialization and background refresh

    viewModelProvider = { viewModel }

    // Check for updates on startup if enabled
    val settings = viewModel.settings.value
    if (settings.autoUpdate.enabled && settings.autoUpdate.checkOnStartup) {
        viewModel.checkForUpdates()
    }

    // Initialize Logging with Kermit
    val logDirPath = "${PlatformPathUtils.getAppDataDir()}/${appConfig.LOGS_DIR_NAME}"
    val logDir = Path(logDirPath)

    Logger.setLogWriters(
        platformLogWriter(),
        FileLogWriter(logDir, getKoin().get(named("LoomScope"))) { viewModel.settings.value.logging }
    )

    // Sync logger severity with settings
    snapshotFlow { viewModel.settings.value.logging.level }.onEach { level ->
        val severity = when (level) {
            LogLevel.Verbose -> Severity.Verbose
            LogLevel.Debug -> Severity.Debug
            LogLevel.Info -> Severity.Info
            LogLevel.Warn -> Severity.Warn
            LogLevel.Error -> Severity.Error
            LogLevel.Assert -> Severity.Assert
        }
        Logger.setMinSeverity(severity)
    }.launchIn(getKoin().get(named("AppScope")))

    Logger.i { "Application started. Logging initialized at: $logDir" }

    updateStatus("Finalizing startup...")
    // Wait for the ViewModel to signal that settings persistence is fully loaded
    viewModel.isLoaded.first { it }

    val startMinimizedOverride = args.contains(appConfig.STARTUP_FLAG_BACKGROUND)
    val startMode = if (startMinimizedOverride) WindowStartMode.Minimized else initialSettings.general.windowStartMode

    return Pair(viewModel, startMode)
}

fun runMain(
    args: Array<String>, 
    splashWindow: JWindow?, 
    preloadedViewModel: SettingsViewModel, 
    preloadedMode: WindowStartMode
) {
    // Flag to handle system tray lifecycle
    var isTrayOpen by mutableStateOf(true)

    application {
        val viewModel = preloadedViewModel
        val startMode = preloadedMode
        val languageCode by viewModel.currentLanguageCode.collectAsState()

        // Sync default JVM locale synchronously before composition
        PlatformLocalization.setApplicationLanguage(languageCode)

        // Provide a root ViewModelStoreOwner for the application to prevent StackOverflow in LocalViewModelStoreOwner
        // on some Compose Multiplatform versions where the default lookup loops.
        val viewModelStoreOwner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                viewModelStoreOwner.viewModelStore.clear()
            }
        }

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

        key(languageCode) {
            Tray(
                state = trayState,
                icon = painterResource(Res.drawable.app_logo),
                tooltip = stringResource(Res.string.app_name),
                onAction = { isVisible = true },
                menu = {
                    Item("Open", onClick = { isVisible = true })
                    Separator()
                    Item("Exit", onClick = { exitApplication() })
                }
            )
        }

        if (isVisible) {
            Window(
                onCloseRequest = {
                    if (viewModel.settings.value.general.closeToTray) {
                        // Just hide the window, background jobs and processes continue to run
                        isVisible = false
                    } else {
                        exitApplication()
                    }
                },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.app_logo),
                state = windowState
            ) {
                // Set minimum window size
                window.minimumSize = Dimension(1000, 600)
                val notificationService = getKoin().get<NotificationService>()

                // Wait until the main Window has actually entered the composition and painted its first frame,
                // then smoothly dispose of the splash screen.
                LaunchedEffect(Unit) {
                    delay(300)
                    splashWindow?.dispose()
                }

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
                    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                        App(viewModel = viewModel)
                    }
                }
            }
        } else {
            // We started minimized, dispose splash screen immediately since there's no main window to wait for
            LaunchedEffect(Unit) {
                splashWindow?.dispose()
            }
        }
    }
}
