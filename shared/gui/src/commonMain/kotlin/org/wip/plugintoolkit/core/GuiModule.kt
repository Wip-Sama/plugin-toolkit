package org.wip.plugintoolkit.core

import org.koin.core.module.Module
import org.koin.dsl.module
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.features.navigation.viewmodel.AppViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginManagerViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel
import org.wip.plugintoolkit.features.settings.definitions.appearanceDefinitions
import org.wip.plugintoolkit.features.settings.definitions.jobDefinitions
import org.wip.plugintoolkit.features.settings.definitions.loggingDefinitions
import org.wip.plugintoolkit.features.settings.definitions.notificationDefinitions
import org.wip.plugintoolkit.features.settings.definitions.pluginDefinitions
import org.wip.plugintoolkit.features.settings.definitions.systemDefinitions
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.build
import org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsSearchViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager

val guiModule: Module = module {
    single { DialogService() }
    single { ShortcutManager(get()) }
    single<SettingsRegistry> {
        val settingsViewModel: SettingsViewModel = get()
        val notificationViewModel: NotificationViewModel = get()
        val appConfig: SystemConfig = get()

        SettingsRegistry.build {
            appearanceDefinitions()
            systemDefinitions(settingsViewModel, appConfig)
            loggingDefinitions(settingsViewModel)
            jobDefinitions()
            notificationDefinitions(notificationViewModel)
            pluginDefinitions()
        }
    }
    single { PluginViewModel(get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get(), get()) }
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
    single { PluginManagerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { (pkg: String) -> PluginSettingsViewModel(pkg, get(), get()) }
    factory { JobViewModel(get()) }
    factory { AppViewModel(get(), get()) }
}
