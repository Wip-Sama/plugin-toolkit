package org.wip.plugintoolkit.features.repository.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.AddRepoResult
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.RepoIndex
import org.wip.plugintoolkit.features.repository.model.RepoValidationResult
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobProgress
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PluginRepoViewModelTest {

    private val repoManager = mockk<RepoManager>(relaxed = true)
    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val flowViewModel = mockk<FlowViewModel>(relaxed = true)
    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val dialogService = mockk<DialogService>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val jobManager = mockk<JobManager>(relaxed = true)
    private val appConfig = mockk<SystemConfig>(relaxed = true)

    private val sampleRepos = listOf(
        ExtensionRepo(name = "Official Remote Repo", url = "https://example.com/repo/index.json"),
        ExtensionRepo(name = "Community Remote Repo", url = "https://community.org/repo/index.json"),
        ExtensionRepo(name = "Local Dev Workspace", url = "D:\\dev\\plugins\\index.json")
    )

    private val samplePlugins = listOf(
        ExtensionPlugin(name = "Zeta Plugin", pkg = "org.zeta", version = "1.0.0", fileName = "zeta.jar"),
        ExtensionPlugin(name = "Alpha Plugin", pkg = "org.alpha", version = "2.1.0", fileName = "alpha.jar"),
        ExtensionPlugin(name = "Beta Plugin", pkg = "org.beta", version = "1.5.0", fileName = "beta.jar")
    )

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { jobManager.jobs } returns MutableStateFlow(emptyList())
        every { jobManager.jobProgress } returns MutableStateFlow(emptyMap())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): PluginRepoViewModel {
        return PluginRepoViewModel(
            repoManager = repoManager,
            pluginManager = pluginManager,
            flowViewModel = flowViewModel,
            notificationService = notificationService,
            dialogService = dialogService,
            settingsRepository = settingsRepository,
            jobManager = jobManager,
            appConfig = appConfig
        )
    }

    @Test
    fun testSegmentedRepoFilteringAndCounts() = runTest {
        val reposFlow = MutableStateFlow(sampleRepos)
        every { repoManager.repositories } returns reposFlow
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Initial counts
        assertEquals(3, viewModel.totalRepoCount.value)
        assertEquals(2, viewModel.remoteRepoCount.value)
        assertEquals(1, viewModel.localRepoCount.value)

        // All tab
        viewModel.repoTypeTab = RepoTypeTab.All
        val allRepos = viewModel.filterRepositories(sampleRepos)
        assertEquals(3, allRepos.size)

        // Remote tab
        viewModel.repoTypeTab = RepoTypeTab.Remote
        val remoteRepos = viewModel.filterRepositories(sampleRepos)
        assertEquals(2, remoteRepos.size)
        assertTrue(remoteRepos.all { it.isRemote })

        // Local tab
        viewModel.repoTypeTab = RepoTypeTab.Local
        val localRepos = viewModel.filterRepositories(sampleRepos)
        assertEquals(1, localRepos.size)
        assertEquals("Local Dev Workspace", localRepos.first().name)
        assertTrue(localRepos.first().isLocal)
    }

    @Test
    fun testRepoSearchFiltering() = runTest {
        val reposFlow = MutableStateFlow(sampleRepos)
        every { repoManager.repositories } returns reposFlow
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())

        val viewModel = createViewModel()

        viewModel.repoSearchQuery = "Community"
        val filtered = viewModel.filterRepositories(sampleRepos)
        assertEquals(1, filtered.size)
        assertEquals("Community Remote Repo", filtered.first().name)
    }

    @Test
    fun testPluginSorting() = runTest {
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())

        val viewModel = createViewModel()

        // Name Ascending
        viewModel.pluginSortMode = PluginSortMode.NameAsc
        val sortedAsc = viewModel.filterAndSortPlugins(samplePlugins)
        assertEquals("Alpha Plugin", sortedAsc[0].name)
        assertEquals("Beta Plugin", sortedAsc[1].name)
        assertEquals("Zeta Plugin", sortedAsc[2].name)

        // Name Descending
        viewModel.pluginSortMode = PluginSortMode.NameDesc
        val sortedDesc = viewModel.filterAndSortPlugins(samplePlugins)
        assertEquals("Zeta Plugin", sortedDesc[0].name)
        assertEquals("Beta Plugin", sortedDesc[1].name)
        assertEquals("Alpha Plugin", sortedDesc[2].name)
    }

    @Test
    fun testPluginFilterChips() = runTest {
        val installedList = listOf(
            InstalledPlugin(pkg = "org.alpha", name = "Alpha Plugin", version = "2.0.0", installPath = "/plugins/org.alpha")
        )
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(installedList)

        val viewModel = createViewModel()

        // Filter Installed
        viewModel.pluginChipFilter = PluginChipFilter.Installed
        val installed = viewModel.filterAndSortPlugins(samplePlugins)
        assertEquals(1, installed.size)
        assertEquals("org.alpha", installed[0].pkg)

        // Filter UpdateAvailable (org.alpha has v2.1.0 in repo vs installed v2.0.0)
        viewModel.pluginChipFilter = PluginChipFilter.UpdateAvailable
        val updateAvailable = viewModel.filterAndSortPlugins(samplePlugins)
        assertEquals(1, updateAvailable.size)
        assertEquals("org.alpha", updateAvailable[0].pkg)

        // Filter NotInstalled
        viewModel.pluginChipFilter = PluginChipFilter.NotInstalled
        val notInstalled = viewModel.filterAndSortPlugins(samplePlugins)
        assertEquals(2, notInstalled.size)
        assertTrue(notInstalled.none { it.pkg == "org.alpha" })
    }

    @Test
    fun testAddRepoDialogFlow() = runTest {
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())
        val dummyIndex = RepoIndex(name = "Valid Local Repo")
        coEvery { repoManager.validateRepository("D:\\plugins\\index.json") } returns RepoValidationResult.Valid(
            name = "Valid Local Repo",
            pluginCount = 5,
            flowCount = 2,
            index = dummyIndex,
            isLocal = true
        )
        coEvery { repoManager.addRepository("D:\\plugins\\index.json") } returns AddRepoResult.Success

        val viewModel = createViewModel()

        // 1. Open dialog for local mode
        viewModel.openAddRepoDialog(isLocal = true)
        assertTrue(viewModel.addRepoDialogState.isOpen)
        assertTrue(viewModel.addRepoDialogState.isLocalMode)
        assertTrue(viewModel.addRepoDialogState.validationResult is RepoValidationResult.Idle)

        // 2. Change target input
        viewModel.onAddRepoTargetChange("D:\\plugins\\index.json")
        assertEquals("D:\\plugins\\index.json", viewModel.addRepoDialogState.targetInput)

        // 3. Trigger verification
        viewModel.validateAddRepoTarget()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.addRepoDialogState.validationResult is RepoValidationResult.Valid)
        val validState = viewModel.addRepoDialogState.validationResult as RepoValidationResult.Valid
        assertEquals("Valid Local Repo", validState.name)

        // 4. Confirm add
        viewModel.confirmAddRepository()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.addRepoDialogState.isOpen)
        assertEquals("", viewModel.addRepoDialogState.targetInput)
        coVerify(exactly = 1) { repoManager.addRepository("D:\\plugins\\index.json") }
    }

    @Test
    fun testIncompatibleFilterChip() = runTest {
        val plugins = listOf(
            ExtensionPlugin(name = "Compatible Plugin", pkg = "org.compat", version = "1.0.0", fileName = "compat.jar", minAppVersion = AppConfig.VERSION),
            ExtensionPlugin(name = "Incompatible Plugin", pkg = "org.incompat", version = "1.0.0", fileName = "incompat.jar", minAppVersion = "99.0.0")
        )
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())

        val viewModel = createViewModel()

        viewModel.pluginChipFilter = PluginChipFilter.Incompatible
        val incompatible = viewModel.filterAndSortPlugins(plugins)
        assertEquals(1, incompatible.size)
        assertEquals("Incompatible Plugin", incompatible.first().name)
    }

    @Test
    fun testFlowFilterAndSorting() = runTest {
        val flows = listOf(
            ExtensionFlow(name = "Zeta Flow", version = "1.0.0", fileName = "zeta.flow.json", minAppVersion = AppConfig.VERSION),
            ExtensionFlow(name = "Alpha Flow", version = "2.0.0", fileName = "alpha.flow.json", minAppVersion = AppConfig.VERSION),
            ExtensionFlow(name = "Beta Flow", version = "1.5.0", fileName = "beta.flow.json", minAppVersion = "99.0.0")
        )
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())
        every { flowViewModel.state } returns MutableStateFlow(
            org.wip.plugintoolkit.features.flows.viewmodel.FlowState(
                flows = listOf(
                    Flow(name = "Alpha Flow")
                )
            )
        )

        val viewModel = createViewModel()

        // 1. Sort Name Asc
        viewModel.pluginSortMode = PluginSortMode.NameAsc
        viewModel.pluginChipFilter = PluginChipFilter.All
        val sortedAsc = viewModel.filterAndSortFlows(flows)
        assertEquals("Alpha Flow", sortedAsc[0].name)
        assertEquals("Beta Flow", sortedAsc[1].name)
        assertEquals("Zeta Flow", sortedAsc[2].name)

        // 2. Filter Installed
        viewModel.pluginChipFilter = PluginChipFilter.Installed
        val installed = viewModel.filterAndSortFlows(flows)
        assertEquals(1, installed.size)
        assertEquals("Alpha Flow", installed.first().name)

        // 3. Filter Incompatible
        viewModel.pluginChipFilter = PluginChipFilter.Incompatible
        val incompatible = viewModel.filterAndSortFlows(flows)
        assertEquals(1, incompatible.size)
        assertEquals("Beta Flow", incompatible.first().name)
    }

    @Test
    fun testPackageSourceOverridesObservation() = runTest {
        val initialSettings = org.wip.plugintoolkit.features.settings.model.AppSettings(
            extensions = org.wip.plugintoolkit.features.settings.model.ExtensionSettings(
                packageSourceOverrides = mapOf("org.alpha" to "https://community.org/repo/index.json")
            )
        )
        val settingsFlow = MutableStateFlow(initialSettings)
        every { settingsRepository.settings } returns settingsFlow
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals("https://community.org/repo/index.json", viewModel.packageSourceOverrides.value["org.alpha"])
    }

    @Test
    fun testInstallPluginDirectly() = runTest {
        val repo1Plugin = ExtensionPlugin(name = "Alpha", pkg = "org.alpha", version = "1.0.0", fileName = "alpha-1.jar", repoUrl = "https://repo1.com")

        every { repoManager.plugins } returns MutableStateFlow(
            mapOf("https://repo1.com" to listOf(repo1Plugin))
        )
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())
        every { settingsRepository.getSettingsDir() } returns "/app/settings"
        every { appConfig.PLUGINS_DIR_NAME } returns "plugins"
        every { settingsRepository.loadSettings() } returns org.wip.plugintoolkit.features.settings.model.AppSettings(
            extensions = org.wip.plugintoolkit.features.settings.model.ExtensionSettings(
                pluginFolders = emptyList()
            )
        )

        val viewModel = createViewModel()
        viewModel.installPlugin(repo1Plugin)
        testScheduler.advanceUntilIdle()

        coVerify { pluginManager.enqueueRemoteInstall(repo1Plugin, "/app/settings/plugins") }
    }

    @Test
    fun testSetPackageSource() = runTest {
        val viewModel = createViewModel()
        viewModel.setPackageSource("org.alpha", "https://repo2.com")
        testScheduler.advanceUntilIdle()
        verify { repoManager.setPackageSourceOverride("org.alpha", "https://repo2.com") }
    }

    @Test
    fun testInstallPluginImmediatelyMarksAsQueued() = runTest {
        val repo1Plugin = ExtensionPlugin(name = "Alpha", pkg = "org.alpha", version = "1.0.0", fileName = "alpha-1.jar", repoUrl = "https://repo1.com")
        every { repoManager.plugins } returns MutableStateFlow(mapOf("https://repo1.com" to listOf(repo1Plugin)))
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())
        every { settingsRepository.getSettingsDir() } returns "/app/settings"
        every { appConfig.PLUGINS_DIR_NAME } returns "plugins"
        every { settingsRepository.loadSettings() } returns org.wip.plugintoolkit.features.settings.model.AppSettings(
            extensions = org.wip.plugintoolkit.features.settings.model.ExtensionSettings(pluginFolders = emptyList())
        )
        val jobsFlow = MutableStateFlow<List<BackgroundJob>>(emptyList())
        val progressFlow = MutableStateFlow<Map<String, JobProgress>>(emptyMap())
        every { jobManager.jobs } returns jobsFlow
        every { jobManager.jobProgress } returns progressFlow

        coEvery { pluginManager.enqueueRemoteInstall(repo1Plugin, any()) } coAnswers {
            jobsFlow.value = listOf(
                BackgroundJob(
                    id = "install_org.alpha_1",
                    name = "Installing Alpha",
                    type = JobType.PluginInstallation,
                    pluginId = "org.alpha",
                    capabilityName = "install",
                    status = JobStatus.Queued
                )
            )
        }

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.activePluginInstallationJobs.value["org.alpha"])

        viewModel.installPlugin(repo1Plugin)
        testScheduler.runCurrent()
        val state = viewModel.activePluginInstallationJobs.value["org.alpha"]
        assertNotNull(state)
        assertEquals(JobStatus.Queued, state.status)

        testScheduler.advanceUntilIdle()
        val stateAfter = viewModel.activePluginInstallationJobs.value["org.alpha"]
        assertNotNull(stateAfter)
        assertEquals(JobStatus.Queued, stateAfter.status)
        coVerify(exactly = 1) { pluginManager.enqueueRemoteInstall(repo1Plugin, "/app/settings/plugins") }
    }

    @Test
    fun testInstallPluginPreventsDuplicateClicks() = runTest {
        val repo1Plugin = ExtensionPlugin(name = "Alpha", pkg = "org.alpha", version = "1.0.0", fileName = "alpha-1.jar", repoUrl = "https://repo1.com")
        every { repoManager.plugins } returns MutableStateFlow(mapOf("https://repo1.com" to listOf(repo1Plugin)))
        every { repoManager.repositories } returns MutableStateFlow(emptyList())
        every { pluginManager.installedPlugins } returns MutableStateFlow(emptyList())
        every { settingsRepository.getSettingsDir() } returns "/app/settings"
        every { appConfig.PLUGINS_DIR_NAME } returns "plugins"
        every { settingsRepository.loadSettings() } returns org.wip.plugintoolkit.features.settings.model.AppSettings(
            extensions = org.wip.plugintoolkit.features.settings.model.ExtensionSettings(pluginFolders = emptyList())
        )
        val jobsFlow = MutableStateFlow<List<BackgroundJob>>(emptyList())
        val progressFlow = MutableStateFlow<Map<String, JobProgress>>(emptyMap())
        every { jobManager.jobs } returns jobsFlow
        every { jobManager.jobProgress } returns progressFlow

        coEvery { pluginManager.enqueueRemoteInstall(repo1Plugin, any()) } coAnswers {
            jobsFlow.value = listOf(
                BackgroundJob(
                    id = "install_org.alpha_1",
                    name = "Installing Alpha",
                    type = JobType.PluginInstallation,
                    pluginId = "org.alpha",
                    capabilityName = "install",
                    status = JobStatus.Queued
                )
            )
        }

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Multiple rapid clicks
        viewModel.installPlugin(repo1Plugin)
        viewModel.installPlugin(repo1Plugin)
        viewModel.installPlugin(repo1Plugin)

        testScheduler.advanceUntilIdle()
        // Only one enqueueRemoteInstall should occur
        coVerify(exactly = 1) { pluginManager.enqueueRemoteInstall(repo1Plugin, "/app/settings/plugins") }
    }

    @Test
    fun testActivePluginInstallationJobsReflectsQueuedAndRunningStates() = runTest {
        val jobsFlow = MutableStateFlow<List<BackgroundJob>>(emptyList())
        val progressFlow = MutableStateFlow<Map<String, JobProgress>>(emptyMap())
        every { jobManager.jobs } returns jobsFlow
        every { jobManager.jobProgress } returns progressFlow

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val installJob = BackgroundJob(
            id = "job-1",
            name = "Installing Alpha",
            type = JobType.PluginInstallation,
            pluginId = "org.alpha",
            capabilityName = "install",
            status = JobStatus.Queued
        )
        jobsFlow.value = listOf(installJob)
        testScheduler.advanceUntilIdle()

        var jobState = viewModel.activePluginInstallationJobs.value["org.alpha"]
        assertNotNull(jobState)
        assertEquals(JobStatus.Queued, jobState.status)
        assertEquals(0f, jobState.progress)

        val runningJob = installJob.copy(status = JobStatus.Running)
        jobsFlow.value = listOf(runningJob)
        progressFlow.value = mapOf("job-1" to JobProgress(mainProgress = 0.65f))
        testScheduler.advanceUntilIdle()

        jobState = viewModel.activePluginInstallationJobs.value["org.alpha"]
        assertNotNull(jobState)
        assertEquals(JobStatus.Running, jobState.status)
        assertEquals(0.65f, jobState.progress)

        val completedJob = installJob.copy(status = JobStatus.Completed)
        jobsFlow.value = listOf(completedJob)
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.activePluginInstallationJobs.value["org.alpha"])
    }

    @Test
    fun testCancelPluginInstallCancelsActiveJobAndClearsState() = runTest {
        val jobsFlow = MutableStateFlow<List<BackgroundJob>>(emptyList())
        val progressFlow = MutableStateFlow<Map<String, JobProgress>>(emptyMap())
        every { jobManager.jobs } returns jobsFlow
        every { jobManager.jobProgress } returns progressFlow

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val queuedJob = BackgroundJob(
            id = "job-queued",
            name = "Installing Alpha",
            type = JobType.PluginInstallation,
            pluginId = "org.alpha",
            capabilityName = "install",
            status = JobStatus.Queued
        )
        jobsFlow.value = listOf(queuedJob)
        testScheduler.advanceUntilIdle()

        viewModel.cancelPluginInstall("org.alpha")
        testScheduler.advanceUntilIdle()

        coVerify { jobManager.cancelJob("job-queued", force = true) }
    }
}
