package org.wip.plugintoolkit.core

import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.wip.plugintoolkit.core.notification.JvmNotificationService
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.utils.DefaultSemanticRegistry
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.RealFileSystem
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.SandboxCleanupManager
import org.wip.plugintoolkit.features.job.logic.SystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLockProvider
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

/**
 * Creates a minimal Koin module for standalone plugin execution.
 * Omits multi-plugin marketplace, repository synchronization, flow editor,
 * and installer services while providing all core services needed to run,
 * configure, and manage background execution of a single plugin.
 */
fun createStandaloneLogicModule(): Module = module {
    single<SemanticRegistry> { DefaultSemanticRegistry() }
    single<SettingsPersistence> { JvmSettingsPersistence() }
    single { SettingsRepository(get(), get(named("LoomScope"))) }
    single<NotificationService> {
        val repository = get<SettingsRepository>()
        JvmNotificationService(get(named("LoomScope"))) {
            repository.settings.value
        }
    }
    single<FileSystem> { RealFileSystem() }
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
    single { PluginLockProvider() }
    single { PluginRegistry(get(), get(named("AppScope")), get(named("LoomDispatcher")), get()) }
    single { PluginLifecycleManager(get(), get(), get(), get()) }
    single { PluginLifecycleCoordinator(get(), get(), get(), get(named("AppScope"))) }
    single {
        PluginManager(
            repoManager = null,
            registry = get(),
            installer = null,
            lifecycleManager = get(),
            scanner = null,
            coordinator = get(),
            folderManager = null,
            scope = get(named("LoomScope"))
        )
    }
    single { SandboxCleanupManager() }
    single { JobManager(get(named("LoomScope")), get()) }
    single<SystemNodeExecutorRegistry> { DefaultSystemNodeExecutorRegistry(get()) }
}
