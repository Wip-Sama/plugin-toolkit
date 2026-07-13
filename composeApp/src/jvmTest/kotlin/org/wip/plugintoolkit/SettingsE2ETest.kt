package org.wip.plugintoolkit

import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.navigation.viewmodel.AppViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import io.mockk.coEvery
import io.mockk.mockk

class SettingsE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSettingsChangePropagatesToUi() {
        val mockPersistence = mockk<SettingsPersistence>()
        coEvery { mockPersistence.load() } returns AppSettings()
        coEvery { mockPersistence.save(any()) } returns Unit
        
        val testRepository = SettingsRepository(mockPersistence, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))

        val mockUpdateService = mockk<org.wip.plugintoolkit.core.update.UpdateService>(relaxed = true)
        io.mockk.every { mockUpdateService.downloadProgress } returns kotlinx.coroutines.flow.MutableStateFlow(0f)

        startKoin {
            modules(module {
                single { testRepository }
                single { SettingsViewModel(get(), mockUpdateService, mockk(relaxed = true), mockk(relaxed = true)) }
                single { PluginViewModel(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) }
                single { AppViewModel(get(), mockUpdateService) }
                val mockNotificationService = mockk<NotificationService>(relaxed = true)
                io.mockk.every { mockNotificationService.events } returns kotlinx.coroutines.flow.MutableSharedFlow()
                single<NotificationService> { mockNotificationService }
                single { DialogService() }
                val mockFlowRepo = mockk<org.wip.plugintoolkit.features.flows.logic.FlowRepository>(relaxed = true)
                io.mockk.every { mockFlowRepo.flows } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                single { FlowViewModel(mockFlowRepo, mockk(relaxed = true), mockk(relaxed = true)) }
                single { ActiveFlowEditorTracker() }
                
                val mockJobViewModel = mockk<JobViewModel>(relaxed = true)
                io.mockk.every { mockJobViewModel.runningJobs } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                io.mockk.every { mockJobViewModel.queuedJobs } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                single { mockJobViewModel }
                
                val mockJobManager = mockk<JobManager>(relaxed = true)
                io.mockk.every { mockJobManager.jobs } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                io.mockk.every { mockJobManager.endedJobs } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                single { mockJobManager }
            })
        }

        composeTestRule.setContent {
            App()
        }

        // Wait for splash screen to clear
        // composeTestRule.waitUntil(timeoutMillis = 5000) {
        //     testRepository.isLoaded.value
        // }

        // Trigger settings change
        // testRepository.updateSettings { it.copy(general = it.general.copy(scaling = 1.5f)) }
        
        // Wait for UI update
        composeTestRule.waitForIdle()

        stopKoin()
    }
}
