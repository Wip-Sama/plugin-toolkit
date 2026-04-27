package com.wip.kpm_cpm_wotoolkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.window.*
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.NotificationViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsSearchViewModel
import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import org.jetbrains.compose.resources.painterResource
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.compose_multiplatform
import org.koin.core.context.startKoin
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.wip.kpm_cpm_wotoolkit.core.logging.FileLogWriter
import com.wip.kpm_cpm_wotoolkit.features.settings.model.LogLevel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import org.koin.dsl.module
import com.wip.kpm_cpm_wotoolkit.core.notification.*
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.RepoManager
import com.wip.kpm_cpm_wotoolkit.features.repository.viewmodel.ModuleRepoViewModel
import androidx.compose.ui.Modifier
import com.wip.kpm_cpm_wotoolkit.features.job.viewmodel.JobViewModel
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.ModuleManagerViewModel
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import kotlinx.coroutines.SupervisorJob
import org.koin.mp.KoinPlatform.getKoin

fun main(args: Array<String>) {
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
            single { com.wip.kpm_cpm_wotoolkit.core.ui.DialogService() }
            single { com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleManager(get(), get()) }
            single { com.wip.kpm_cpm_wotoolkit.features.job.logic.JobManager(CoroutineScope(SupervisorJob() + Dispatchers.Default), repository.loadSettings().jobs.maxConcurrentJobs) }
            factory { SettingsViewModel(get()) }
            factory { NotificationViewModel(get()) }
            factory { SettingsSearchViewModel(get()) }
            factory { ModuleRepoViewModel(get(), get(), get(), get(), get()) }
            factory { ModuleManagerViewModel(get(), get(), get()) }
            factory { JobViewModel(get()) }
            factory { PluginViewModel(get(), get(), get()) }
        })
    }

    val koin = getKoin()
    val viewModel = koin.get<SettingsViewModel>()
    koin.get<RepoManager>() // Trigger initialization and background refresh
    val moduleManager = koin.get<com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleManager>()
    
    // Load enabled modules at startup
    moduleManager.installedModules.value.forEach { module ->
        if (module.isEnabled) {
            val result = moduleManager.loadModule(module.pkg)
            if (result.isFailure) {
                Logger.e { "Failed to load module ${module.pkg}: ${result.exceptionOrNull()?.message}" }
            }
        }
    }
    
    viewModelProvider = { viewModel }

    // Initialize Logging with Kermit
    val logDir = File(System.getProperty("user.home"), KeepTrack.SETTINGS_DIR_NAME + File.separator + KeepTrack.LOGS_DIR_NAME)
    
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
    
    Logger.i { "Application started. Logging initialized at: ${logDir.absolutePath}" }
    
    // Check if we should start minimized
    val startMinimized = args.contains(KeepTrack.STARTUP_FLAG_BACKGROUND) || initialSettings.general.startMinimized

    application {
        val trayState = rememberTrayState()
        var isVisible by remember { mutableStateOf(!startMinimized) }

        Tray(
            state = trayState,
            icon = painterResource(Res.drawable.compose_multiplatform),
            tooltip = "WOToolkit",
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
                title = "WOToolkit",
            ) {
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
