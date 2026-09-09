package org.wip.plugintoolkit

import androidx.compose.foundation.ComposeFoundationFlags
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.core.DefaultSystemConfig
import org.wip.plugintoolkit.core.PortableSystemConfig
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.coroutineModule
import org.wip.plugintoolkit.core.guiModule
import org.wip.plugintoolkit.core.logging.FileLogWriter
import org.wip.plugintoolkit.core.logicModule
import org.wip.plugintoolkit.core.notification.JvmNotificationService
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.PlatformLocalization
import org.wip.plugintoolkit.core.utils.PlatformPathUtils
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppTheme
import org.wip.plugintoolkit.features.settings.model.LogLevel
import org.wip.plugintoolkit.features.settings.model.WindowStartMode
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.ui.splash.showSplashWindow
import org.wip.plugintoolkit.ui.titlebar.LocalWindowController
import org.wip.plugintoolkit.ui.titlebar.LocalWindowScope
import org.wip.plugintoolkit.ui.titlebar.WindowController
import org.wip.plugintoolkit.ui.titlebar.WindowFrameUtils
import org.wip.plugintoolkit.ui.titlebar.WindowResizeOverlay
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_logo
import plugintoolkit.composeapp.generated.resources.app_name
import java.awt.Dimension
import java.io.File
import javax.swing.JOptionPane
import javax.swing.JOptionPane.showMessageDialog
import javax.swing.JWindow
import kotlin.system.exitProcess

private const val WINDOW_MIN_WIDTH = 680
private const val WINDOW_MIN_HEIGHT = 480

fun detectSystemConfig(): SystemConfig {
    val userDir = File(System.getProperty("user.dir"))
    if (File(userDir, ".portable").exists()) {
        Logger.i { "Startup: Portable marker found in user.dir (${userDir.absolutePath})" }
        return PortableSystemConfig(userDir.absolutePath)
    }

    try {
        val location = SystemConfig::class.java.protectionDomain.codeSource?.location
        if (location != null) {
            val file = File(location.toURI())
            val parent = file.parentFile
            if (parent != null) {
                if (File(parent, ".portable").exists()) {
                    Logger.i { "Startup: Portable marker found in JAR parent dir (${parent.absolutePath})" }
                    return PortableSystemConfig(parent.absolutePath)
                }
                val grandParent = parent.parentFile
                if (grandParent != null && File(grandParent, ".portable").exists()) {
                    Logger.i { "Startup: Portable marker found in app root dir (${grandParent.absolutePath})" }
                    return PortableSystemConfig(grandParent.absolutePath)
                }
            }
        }
    } catch (e: Throwable) {
        Logger.w(e) { "Startup: Failed to inspect codeSource location for .portable marker" }
    }

    Logger.i { "Startup: Running in standard system installation mode" }
    return DefaultSystemConfig()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun main(args: Array<String>) {
    ComposeFoundationFlags.isNewContextMenuEnabled = true
    val splashWindow = try {
        showSplashWindow()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
    try {
        // Run startup on an IO thread while the AWT EDT freely animates the splash screen
        val (viewModel, mode) = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
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
        exitProcess(1)
    }
}

suspend fun performStartup(args: Array<String>, updateStatus: (String) -> Unit = {}): Pair<SettingsViewModel, WindowStartMode> {
    updateStatus("Loading configuration...")
    val detectedConfig = detectSystemConfig()

    var viewModelProvider: () -> SettingsViewModel? = { null }

    updateStatus("Initializing Dependency Injection...")
    startKoin {
        modules(
            coroutineModule,
            module {
                single<SystemConfig> { detectedConfig }
                single<SettingsPersistence> { JvmSettingsPersistence() }
                single<NotificationService> {
                    val repository = get<SettingsRepository>()
                    JvmNotificationService(get(named("LoomScope"))) {
                        viewModelProvider()?.settings?.value ?: repository.settings.value
                    }
                }
            },
            logicModule,
            guiModule
        )
    }

    val koin = getKoin()
    val persistence = koin.get<SettingsPersistence>()
    val initialSettings = persistence.load()

    // ── Single Instance System Lock Check ────────────────────────
    if (initialSettings.general.singleInstanceLock) {
        val appDataDir = File(detectedConfig.getAppDataDir())
        when (val lockResult = org.wip.plugintoolkit.core.utils.AppLockManager.acquireLock(appDataDir)) {
            is org.wip.plugintoolkit.core.utils.LockResult.Acquired -> {
                Logger.i { "Single instance lock acquired successfully" }
            }
            is org.wip.plugintoolkit.core.utils.LockResult.AlreadyRunning -> {
                Logger.w { "Another instance of PluginToolkit is already running (PID: ${lockResult.pid})" }
                val options = arrayOf("Exit", "Clear Lock & Force Start")
                val choice = javax.swing.JOptionPane.showOptionDialog(
                    null,
                    "Another instance of PluginToolkit is currently running${lockResult.pid?.let { " (PID: $it)" } ?: ""}.\n" +
                            "Running multiple instances simultaneously may cause data corruption or unexpected conflicts.\n\n" +
                            "Would you like to exit or clear the lock and start anyway?",
                    "PluginToolkit Already Running",
                    javax.swing.JOptionPane.DEFAULT_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
                )
                if (choice == 1) {
                    Logger.w { "User chose to clear lock and force start" }
                    val forced = org.wip.plugintoolkit.core.utils.AppLockManager.forceClearAndAcquire(appDataDir)
                    if (forced !is org.wip.plugintoolkit.core.utils.LockResult.Acquired) {
                        javax.swing.JOptionPane.showMessageDialog(
                            null,
                            "Failed to clear lock and start application.",
                            "Lock Error",
                            javax.swing.JOptionPane.ERROR_MESSAGE
                        )
                        exitProcess(1)
                    }
                } else {
                    exitProcess(0)
                }
            }
            is org.wip.plugintoolkit.core.utils.LockResult.Error -> {
                Logger.e { "Error checking single instance lock: ${lockResult.message}" }
            }
        }
    }

    val viewModel = koin.get<SettingsViewModel>()
    val registry = koin.get<PluginRegistry>()
    val pluginManager = koin.get<PluginManager>()
    val appScope = koin.get<CoroutineScope>(named("AppScope"))
    val settingsRepository = koin.get<SettingsRepository>()
    val updateService = koin.get<UpdateService>()
    val appConfig = koin.get<SystemConfig>()

    viewModelProvider = { viewModel }

    // Initialize Logging with Kermit immediately so startup & plugin initialization logs are captured
    val logDirPath = "${PlatformPathUtils.getAppDataDir()}/${appConfig.LOGS_DIR_NAME}"
    val logDir = Path(logDirPath)

    Logger.setLogWriters(
        platformLogWriter(),
        FileLogWriter(logDir, koin.get(named("LoomScope"))) {
            viewModelProvider()?.settings?.value?.logging ?: initialSettings.logging
        }
    )

    val initialSeverity = when (initialSettings.logging.level) {
        LogLevel.Verbose -> Severity.Verbose
        LogLevel.Debug -> Severity.Debug
        LogLevel.Info -> Severity.Info
        LogLevel.Warn -> Severity.Warn
        LogLevel.Error -> Severity.Error
        LogLevel.Assert -> Severity.Assert
    }
    Logger.setMinSeverity(initialSeverity)

    // Sync logger severity with settings changes dynamically
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
    }.launchIn(appScope)

    // Dynamically sync single-instance lock with settings changes
    snapshotFlow { viewModel.settings.value.general.singleInstanceLock }.onEach { isEnabled ->
        val appDataDir = File(detectedConfig.getAppDataDir())
        if (isEnabled) {
            if (!org.wip.plugintoolkit.core.utils.AppLockManager.isLockAcquired()) {
                org.wip.plugintoolkit.core.utils.AppLockManager.acquireLock(appDataDir)
            }
        } else {
            org.wip.plugintoolkit.core.utils.AppLockManager.releaseLock()
        }
    }.launchIn(appScope)

    Logger.i { "Application starting. Logging initialized at: $logDir with minSeverity=$initialSeverity" }

    updateStatus("Cleaning up updates...")
    updateService.cleanupOldUpdates(settingsRepository.getSettingsDir())

    updateStatus("Initializing plugins...")
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

    // Check for updates on startup if enabled
    val settings = viewModel.settings.value
    if (settings.autoUpdate.enabled && settings.autoUpdate.checkOnStartup) {
        viewModel.checkForUpdates()
    }

    updateStatus("Finalizing startup...")
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
    var isTrayOpen by mutableStateOf(true)
    application {
        val viewModel = preloadedViewModel
        val startMode = preloadedMode
        val languageCode by viewModel.currentLanguageCode.collectAsState()

        PlatformLocalization.setApplicationLanguage(languageCode)

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
            size = DpSize(ToolkitTheme.dimensions.dialogMaxWidthLarge, ToolkitTheme.dimensions.dialogMaxHeight)
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

        val appSettings by viewModel.settings.collectAsState()
        val useCustomTitleBar = appSettings.appearance.useCustomTitleBar

        if (isVisible) {
            key(useCustomTitleBar) {
                val isMaximized = windowState.placement == WindowPlacement.Maximized
                val windowController = remember(windowState, isMaximized) {
                    WindowController(
                        isMaximized = isMaximized,
                        onMinimize = { windowState.isMinimized = true },
                        onMaximizeToggle = {
                            windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                        },
                        onClose = {
                            if (viewModel.settings.value.general.closeToTray) {
                                isVisible = false
                            } else {
                                exitApplication()
                            }
                        }
                    )
                }

                Window(
                    onCloseRequest = {
                        if (viewModel.settings.value.general.closeToTray) {
                            isVisible = false
                        } else {
                            exitApplication()
                        }
                    },
                    title = stringResource(Res.string.app_name),
                    icon = painterResource(Res.drawable.app_logo),
                    state = windowState,
                    undecorated = useCustomTitleBar
                ) {
                    window.minimumSize = Dimension(WINDOW_MIN_WIDTH, WINDOW_MIN_HEIGHT)
                    val notificationService = getKoin().get<NotificationService>()

                    LaunchedEffect(useCustomTitleBar) {
                        if (useCustomTitleBar) {
                            WindowFrameUtils.enableUndecoratedDropShadow(window)
                        } else {
                            val isDark = when (appSettings.appearance.theme) {
                                AppTheme.Dark, AppTheme.Amoled -> true
                                AppTheme.Light -> false
                                AppTheme.System -> true
                            }
                            WindowFrameUtils.setNativeTitleBarTheme(
                                window = window,
                                isDark = isDark
                            )
                        }
                    }

                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(300)
                        splashWindow?.dispose()
                    }

                    LaunchedEffect(Unit) {
                        val currentTrayState = trayState
                        notificationService.events.collect { event ->
                            if (event is NotificationEvent.System) {
                                currentTrayState.sendNotification(
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
                        CompositionLocalProvider(
                            LocalViewModelStoreOwner provides viewModelStoreOwner,
                            LocalWindowScope provides this@Window,
                            LocalWindowController provides windowController
                        ) {
                            App(viewModel = viewModel)
                        }

                        if (useCustomTitleBar) {
                            WindowResizeOverlay()
                        }
                    }
                }
            }
        } else {
            LaunchedEffect(Unit) {
                splashWindow?.dispose()
            }
        }
    }
}
