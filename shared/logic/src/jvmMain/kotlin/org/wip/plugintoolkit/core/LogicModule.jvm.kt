package org.wip.plugintoolkit.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.wip.plugintoolkit.core.notification.JvmNotificationService
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.DefaultSemanticRegistry
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.RealFileSystem
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.logic.FlowExecutionGuard
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.logic.ReactiveCapabilityLockTracker
import org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.SandboxCleanupManager
import org.wip.plugintoolkit.features.job.logic.SystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginFolderManager
import org.wip.plugintoolkit.features.plugin.logic.PluginInstaller
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLockProvider
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginScanner
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

actual val logicModule: Module = module {
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
    single { PluginLockProvider() }
    single { PluginRegistry(get(), get(named("AppScope")), get(named("LoomDispatcher")), get()) }
    single { PluginLifecycleManager(get(), get(), get(), get()) }
    single { PluginLifecycleCoordinator(get(), get(), get(), get(named("AppScope"))) }
    single { PluginFolderManager(get(), get(), get()) }
    single { PluginInstaller(get(), get(), get(), get(), get(), get(), get()) }
    single { PluginScanner(get(), get()) }
    single { PluginManager(get(), get(), get(), get(), get(), get(), get(), get(named("LoomScope"))) }
    single { SandboxCleanupManager() }
    single { JobManager(get(named("LoomScope")), get()) }
    single { FlowExecutionGuard { getOrNull<JobManager>() } }
    single { FlowRepository(get(), get(), get(named("LoomScope")), get(), get()) }
    single { ReactiveCapabilityLockTracker(get()) }
    single<SystemNodeExecutorRegistry> { DefaultSystemNodeExecutorRegistry(get()) }
    single { UpdateService(get(), get()) }
}
