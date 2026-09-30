package org.wip.plugintoolkit.features.plugin.viewmodel

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.viewmodel.FlowState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PluginManagerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val dialogService = mockk<DialogService>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val repoManager = mockk<RepoManager>(relaxed = true)
    private val jobManager = mockk<JobManager>(relaxed = true)
    private val flowViewModel = mockk<FlowViewModel>(relaxed = true)
    private val appConfig = mockk<SystemConfig>(relaxed = true)
    private val notificationService = mockk<NotificationService>(relaxed = true)

    private val installedPluginsFlow = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    private val repoPluginsFlow = MutableStateFlow<Map<String, List<ExtensionPlugin>>>(emptyMap())
    private val repositoriesFlow = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    private val settingsFlow = MutableStateFlow(AppSettings())
    private val flowStateFlow = MutableStateFlow(FlowState())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { pluginManager.installedPlugins } returns installedPluginsFlow
        every { pluginManager.loadedPlugins } returns MutableStateFlow(emptySet())
        every { pluginManager.loadingPlugins } returns MutableStateFlow(emptySet())
        every { pluginManager.pluginLoadingSteps } returns MutableStateFlow(emptyMap())
        every { pluginManager.isRegistryReady } returns MutableStateFlow(true)
        every { repoManager.plugins } returns repoPluginsFlow
        every { repoManager.repositories } returns repositoriesFlow
        every { settingsRepository.settings } returns settingsFlow
        every { settingsRepository.getSettingsDir() } returns "/app/settings"
        every { appConfig.PLUGINS_DIR_NAME } returns "plugins"
        every { jobManager.jobProgress } returns MutableStateFlow(emptyMap())
        every { jobManager.jobs } returns MutableStateFlow(emptyList())
        every { flowViewModel.state } returns flowStateFlow
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): PluginManagerViewModel {
        return PluginManagerViewModel(
            pluginManager = pluginManager,
            dialogService = dialogService,
            settingsRepository = settingsRepository,
            repoManager = repoManager,
            jobManager = jobManager,
            flowViewModel = flowViewModel,
            appConfig = appConfig,
            notificationService = notificationService
        )
    }

    @Test
    fun testDetectNewerVersionInAlternateRepo() = runTest(testDispatcher) {
        val installed = InstalledPlugin(
            pkg = "org.wip.vision",
            name = "Vision",
            version = "1.0.1",
            installPath = "/plugins/org.wip.vision",
            repoUrl = "https://repo-official.com"
        )
        val officialRepo = ExtensionRepo(name = "Official Repo", url = "https://repo-official.com")
        val communityRepo = ExtensionRepo(name = "Community Repo", url = "https://repo-community.com")

        val officialPlugin = ExtensionPlugin(
            name = "Vision",
            pkg = "org.wip.vision",
            version = "1.0.1",
            fileName = "vision-1.0.1.jar",
            repoUrl = "https://repo-official.com"
        )
        val communityPlugin = ExtensionPlugin(
            name = "Vision",
            pkg = "org.wip.vision",
            version = "1.0.2",
            fileName = "vision-1.0.2.jar",
            repoUrl = "https://repo-community.com"
        )

        installedPluginsFlow.value = listOf(installed)
        repositoriesFlow.value = listOf(officialRepo, communityRepo)
        repoPluginsFlow.value = mapOf(
            "https://repo-official.com" to listOf(officialPlugin),
            "https://repo-community.com" to listOf(communityPlugin)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val altUpdate = viewModel.alternateRepoUpdates.value["org.wip.vision"]
        assertNotNull(altUpdate)
        assertEquals("1.0.2", altUpdate.newerVersion)
        assertEquals("Community Repo", altUpdate.repo.name)
    }

    @Test
    fun testNoAlternateUpdateWhenCurrentRepoHasSameOrHigherVersion() = runTest(testDispatcher) {
        val installed = InstalledPlugin(
            pkg = "org.wip.vision",
            name = "Vision",
            version = "1.0.2",
            installPath = "/plugins/org.wip.vision",
            repoUrl = "https://repo-official.com"
        )
        val officialRepo = ExtensionRepo(name = "Official Repo", url = "https://repo-official.com")
        val communityRepo = ExtensionRepo(name = "Community Repo", url = "https://repo-community.com")

        val officialPlugin = ExtensionPlugin(
            name = "Vision",
            pkg = "org.wip.vision",
            version = "1.0.2",
            fileName = "vision-1.0.2.jar",
            repoUrl = "https://repo-official.com"
        )
        val communityPlugin = ExtensionPlugin(
            name = "Vision",
            pkg = "org.wip.vision",
            version = "1.0.1",
            fileName = "vision-1.0.1.jar",
            repoUrl = "https://repo-community.com"
        )

        installedPluginsFlow.value = listOf(installed)
        repositoriesFlow.value = listOf(officialRepo, communityRepo)
        repoPluginsFlow.value = mapOf(
            "https://repo-official.com" to listOf(officialPlugin),
            "https://repo-community.com" to listOf(communityPlugin)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val altUpdate = viewModel.alternateRepoUpdates.value["org.wip.vision"]
        assertNull(altUpdate)
    }

    @Test
    fun testSwitchSourceRepoAndUpdate() = runTest(testDispatcher) {
        val communityRepo = ExtensionRepo(name = "Community Repo", url = "https://repo-community.com")
        repositoriesFlow.value = listOf(communityRepo)

        val updatePlugin = ExtensionPlugin(
            name = "Vision",
            pkg = "org.wip.vision",
            version = "1.0.2",
            fileName = "vision-1.0.2.jar",
            repoUrl = "https://repo-community.com"
        )
        every { pluginManager.getUpdate("org.wip.vision") } returns updatePlugin

        val viewModel = createViewModel()
        viewModel.switchSourceRepoAndUpdate("org.wip.vision", "https://repo-community.com")
        advanceUntilIdle()

        verify { repoManager.setPackageSourceOverride("org.wip.vision", "https://repo-community.com") }
        coVerify { pluginManager.updateRemote("org.wip.vision") }
    }

    @Test
    fun testLockedPluginsIncludesRunningCapabilityJobs() = runTest(testDispatcher) {
        val jobsFlow = MutableStateFlow(
            listOf(
                BackgroundJob(
                    id = "cap-1",
                    name = "Capability Job",
                    type = JobType.Capability,
                    pluginId = "org.wip.plugin.a",
                    capabilityName = "run"
                ).copy(status = JobStatus.Running)
            )
        )
        every { jobManager.jobs } returns jobsFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.lockedPlugins.value.contains("org.wip.plugin.a"), "Running Capability job should lock the plugin")
    }

    @Test
    fun testLockedPluginsIncludesQueuedFlowJobs() = runTest(testDispatcher) {
        val jobsFlow = MutableStateFlow(
            listOf(
                BackgroundJob(
                    id = "flow-1",
                    name = "Flow Job",
                    type = JobType.Flow,
                    pluginId = "org.wip.plugin.b",
                    capabilityName = "my-flow"
                ).copy(status = JobStatus.Queued)
            )
        )
        every { jobManager.jobs } returns jobsFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.lockedPlugins.value.contains("org.wip.plugin.b"), "Queued Flow job should lock the plugin")
    }

    @Test
    fun testLockedPluginsExcludesInstallationAndCompletedJobs() = runTest(testDispatcher) {
        val jobsFlow = MutableStateFlow(
            listOf(
                BackgroundJob(
                    id = "install-1",
                    name = "Installation Job",
                    type = JobType.PluginInstallation,
                    pluginId = "org.wip.plugin.c",
                    capabilityName = "install"
                ).copy(status = JobStatus.Running),
                BackgroundJob(
                    id = "cap-done",
                    name = "Completed Capability",
                    type = JobType.Capability,
                    pluginId = "org.wip.plugin.d",
                    capabilityName = "run"
                ).copy(status = JobStatus.Completed)
            )
        )
        every { jobManager.jobs } returns jobsFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.lockedPlugins.value.isEmpty(), "Installation and completed jobs must not lock plugins")
    }

    @Test
    fun testLockedPluginsIncludesPluginsUsedInsideRunningFlowJobs() = runTest(testDispatcher) {
        val flow = Flow(
            name = "Flow With Plugin",
            nodes = listOf(
                Node.CapabilityNode(
                    id = 1L,
                    position = Offset.Zero,
                    pluginInfo = PluginInfo(id = "org.wip.plugin.nested", name = "Nested", version = "1.0.0", description = "test"),
                    capability = Capability(name = "doSomething", description = "test", returnType = DataType.Primitive(PrimitiveType.STRING)),
                    inputs = emptyList(),
                    outputs = emptyList()
                )
            )
        )
        flowStateFlow.value = FlowState(flows = listOf(flow))

        val jobsFlow = MutableStateFlow(
            listOf(
                BackgroundJob(
                    id = "flow-run-1",
                    name = "Flow Execution",
                    type = JobType.Flow,
                    pluginId = "system",
                    capabilityName = "Flow With Plugin"
                ).copy(status = JobStatus.Running)
            )
        )
        every { jobManager.jobs } returns jobsFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(
            viewModel.lockedPlugins.value.contains("org.wip.plugin.nested"),
            "Plugin used inside active flow must be marked as locked in lockedPlugins"
        )
    }
}
