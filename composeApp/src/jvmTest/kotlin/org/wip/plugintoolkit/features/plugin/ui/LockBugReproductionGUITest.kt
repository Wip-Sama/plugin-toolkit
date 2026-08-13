package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.api.SettingMetadata
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LockBugReproductionGUITest {

    private lateinit var mockManager: PluginManager
    private lateinit var mockJobManager: JobManager
    private lateinit var mockNotificationService: NotificationService

    @Before
    fun setUp() {
        mockManager = io.mockk.mockk<PluginManager>(relaxed = true)
        mockJobManager = io.mockk.mockk<JobManager>(relaxed = true)
        mockNotificationService = io.mockk.mockk<NotificationService>(relaxed = true)

        io.mockk.every { mockManager.pluginLocksState } returns MutableStateFlow(emptyMap())
        io.mockk.every { mockManager.pluginSettingsState } returns MutableStateFlow(emptyMap())
        io.mockk.every { mockManager.loadedPlugins } returns MutableStateFlow(setOf("org.wip.complete"))
        io.mockk.every { mockJobManager.jobs } returns MutableStateFlow(emptyList())

        startKoin {
            modules(module {
                single { mockManager }
                single { mockJobManager }
                single { mockNotificationService }
                val semanticRegistryMock = io.mockk.mockk<SemanticRegistry>(relaxed = true)
                io.mockk.every { semanticRegistryMock.getCategory(any()) } returns null
                single { semanticRegistryMock }
                factory { (pkg: String) -> PluginSettingsViewModel(pkg, mockManager, mockJobManager) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * Bug 1a Test:
     * In PluginSettingsDialog, when a custom setting (like apiKey) unlocks enum options in capabilities (like ONLINE),
     * PluginSettingsDialog displays an informative badge "Unlocks Enum Options".
     */
    @Test
    fun testBug1a_pluginSettingsDialogDisplaysUnlocksEnumOptionsBadge() = runDesktopComposeUiTest {
        val enumType = DataType.Enum(
            className = "org.wip.complete.FeatureMode",
            options = listOf("ONLINE", "SECURE", "EXPERIMENTAL", "LOCAL"),
            optionRequirements = mapOf("ONLINE" to listOf("apiKey"))
        )
        val cap = Capability(
            name = "capabilityWithFlowContext",
            description = "Test capability",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            parameters = mapOf("mode" to ParameterMetadata(description = "Mode", type = enumType))
        )
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo("org.wip.complete", "Complete Plugin", "1.0", "Description"),
            requirements = Requirements(512, 5000),
            settings = mapOf("apiKey" to SettingMetadata(description = "API Key", type = DataType.Primitive(PrimitiveType.STRING))),
            capabilities = listOf(cap)
        )

        io.mockk.every { mockManager.getManifest("org.wip.complete") } returns manifest

        setContent {
            PluginSettingsDialog(
                pkg = "org.wip.complete",
                onDismiss = {}
            )
        }

        // The custom setting badge should informatively display "Unlocks Enum Options"
        onNodeWithText("Unlocks Enum Options").assertExists()
    }

    /**
     * Bug 1b Test:
     * In PluginContent / CapabilityTester, when custom settings (apiKey & secretToken) are saved in PluginSettingsStore.settings,
     * PluginContent MUST pass them to CapabilityTester so unlocked options like SECURE are enabled in the dropdown menu.
     */
    @Test
    fun testBug1b_capabilityTesterDropdownEnablesUnlockedOptionsWhenSettingsAreProvided() = runDesktopComposeUiTest {
        val enumType = DataType.Enum(
            className = "org.wip.complete.FeatureMode",
            options = listOf("ONLINE", "SECURE", "EXPERIMENTAL", "LOCAL"),
            optionRequirements = mapOf("ONLINE" to listOf("apiKey"), "SECURE" to listOf("secretToken")),
            optionLockRequirements = mapOf("EXPERIMENTAL" to listOf("feature_unlocked"))
        )
        val cap = Capability(
            name = "capabilityWithFlowContext",
            description = "Test capability",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            parameters = mapOf("mode" to ParameterMetadata(description = "Mode", type = enumType))
        )

        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo("org.wip.complete", "Complete Plugin", "1.0", "Description"),
            requirements = Requirements(512, 5000),
            capabilities = listOf(cap)
        )

        val mockEntry = io.mockk.mockk<PluginEntry>(relaxed = true)
        io.mockk.every { mockEntry.getManifest() } returns Result.success(manifest)
        io.mockk.every { mockManager.pluginSettingsState } returns MutableStateFlow(
            mapOf(
                "org.wip.complete" to PluginSettingsStore(
                    settings = mapOf(
                        "apiKey" to JsonPrimitive("key123"),
                        "secretToken" to JsonPrimitive("token123")
                    )
                )
            )
        )

        val viewModel = PluginViewModel(mockJobManager, mockNotificationService, mockManager)
        viewModel.selectPlugin(mockEntry)
        viewModel.selectCapability(cap)

        setContent {
            PluginContent(viewModel = viewModel)
        }

        // Open dropdown by clicking trigger button
        onNodeWithText("ONLINE").performClick()

        // Inside the opened popup, SECURE MUST be enabled because secretToken IS provided in PluginSettingsStore.settings!
        onNodeWithText("SECURE").assertIsEnabled()
    }

    /**
     * Bug 2 Test:
     * When a capability is tested in CapabilityTester with a lock that is false,
     * the locked enum option (EXPERIMENTAL) MUST be disabled inside the dropdown menu.
     */
    @Test
    fun testBug2_capabilityTesterDisablesLockedEnumOptionWhenLockIsFalse() = runDesktopComposeUiTest {
        val enumType = DataType.Enum(
            className = "org.wip.complete.FeatureMode",
            options = listOf("ONLINE", "SECURE", "EXPERIMENTAL", "LOCAL"),
            optionLockRequirements = mapOf("EXPERIMENTAL" to listOf("feature_unlocked"))
        )
        val capability = Capability(
            name = "capabilityWithFlowContext",
            description = "Test capability with enum lock requirement",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            parameters = mapOf(
                "mode" to ParameterMetadata(description = "Mode", type = enumType, required = true)
            )
        )

        setContent {
            CapabilityTester(
                capability = capability,
                parameterValues = mapOf("mode" to "ONLINE"),
                onParameterChange = { _, _ -> },
                isParameterAutoGenerated = { false },
                saveResults = false,
                onSaveResultsChange = {},
                activeJobs = emptyList(),
                providedLocks = mapOf("feature_unlocked" to false), // Lock is false!
                onExecute = {}
            )
        }

        // Click dropdown trigger button to open
        onNodeWithText("ONLINE").performClick()

        // EXPERIMENTAL option MUST be disabled
        onNodeWithText("EXPERIMENTAL").assertIsNotEnabled()
    }

    /**
     * Bug 3 Test:
     * In PluginContent, settings are loaded via pluginManager.loadPluginSettings(pluginId).
     * When PluginSettingsStore contains userId, PluginContent passes providedSettings to CapabilityTester,
     * unlocking the capability (no "Requires setting: userId" message).
     */
    @Test
    fun testBug3_pluginContentPassesSavedSettingsToCapabilityTester() = runDesktopComposeUiTest {
        val cap = Capability(
            name = "capabilityWithFlowContext",
            description = "Test capability requiring userId setting",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            requiresSettings = listOf("userId")
        )

        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo("org.wip.complete", "Complete Plugin", "1.0", "Description"),
            requirements = Requirements(512, 5000),
            capabilities = listOf(cap)
        )

        val mockEntry = io.mockk.mockk<PluginEntry>(relaxed = true)
        io.mockk.every { mockEntry.getManifest() } returns Result.success(manifest)

        // Mock pluginSettingsState to return PluginSettingsStore with settings (where user entered userId)
        io.mockk.every { mockManager.pluginSettingsState } returns MutableStateFlow(
            mapOf("org.wip.complete" to PluginSettingsStore(
                settings = mapOf("userId" to JsonPrimitive("1234123412"))
            ))
        )

        val viewModel = PluginViewModel(mockJobManager, mockNotificationService, mockManager)
        viewModel.selectPlugin(mockEntry)
        viewModel.selectCapability(cap)

        setContent {
            PluginContent(viewModel = viewModel)
        }

        // When userId setting IS provided in PluginSettingsStore.settings,
        // PluginContent passes it down so "Requires setting: userId" DOES NOT exist.
        onNodeWithText("Requires setting: userId").assertDoesNotExist()
    }

    /**
     * Bug 1d Test:
     * When a setting is updated in PluginSettingsViewModel (in PluginSettingsDialog),
     * locks MUST be refreshed dynamically for the updated store in real-time.
     */
    @Test
    fun testBug1d_pluginSettingsViewModelRefreshesLocksOnSettingUpdate() = runDesktopComposeUiTest {
        val enumType = DataType.Enum(
            className = "org.wip.complete.FeatureMode",
            options = listOf("ONLINE", "SECURE", "EXPERIMENTAL"),
            optionLockRequirements = mapOf("EXPERIMENTAL" to listOf("feature_unlocked"))
        )
        val cap = Capability(
            name = "cap1",
            description = "Cap 1",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            parameters = mapOf("mode" to ParameterMetadata(description = "Mode", type = enumType))
        )
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo("org.wip.complete", "Complete Plugin", "1.0", "Description"),
            requirements = Requirements(512, 5000),
            settings = mapOf("secretToken" to SettingMetadata(description = "Secret Token", type = DataType.Primitive(PrimitiveType.STRING))),
            capabilities = listOf(cap)
        )

        val locksFlow = MutableStateFlow(mapOf("org.wip.complete" to mapOf("feature_unlocked" to false)))

        io.mockk.coEvery { mockManager.refreshLocks("org.wip.complete", any()) } answers {
            val store = secondArg<PluginSettingsStore?>()
            val unlocked = store?.settings?.get("secretToken")?.let { (it as? JsonPrimitive)?.content?.isNotBlank() } == true
            val locks = mapOf("feature_unlocked" to unlocked)
            locksFlow.value = mapOf("org.wip.complete" to locks)
            locks
        }
        io.mockk.every { mockManager.getManifest("org.wip.complete") } returns manifest
        io.mockk.every { mockManager.loadPluginSettings("org.wip.complete") } returns PluginSettingsStore()
        io.mockk.every { mockManager.pluginLocksState } returns locksFlow

        val viewModel = PluginSettingsViewModel("org.wip.complete", mockManager, mockJobManager)

        setContent {
            PluginSettingsDialog(
                pkg = "org.wip.complete",
                onDismiss = {},
                viewModel = viewModel
            )
        }

        // Open dropdown menu
        onAllNodesWithText("ONLINE")[0].performClick()
        // Before updating setting, EXPERIMENTAL option is disabled
        onNodeWithText("EXPERIMENTAL").assertIsNotEnabled()
        // Close dropdown menu
        onAllNodesWithText("ONLINE")[0].performClick()

        // Update setting in memory
        viewModel.updateSetting("secretToken", JsonPrimitive("token123"))

        // Allow background coroutines to process lock calculations
        Thread.sleep(200)
        waitForIdle()

        // Re-open dropdown menu to inspect updated state
        onAllNodesWithText("ONLINE")[0].performClick()

        // EXPERIMENTAL option MUST be enabled now!
        onNodeWithText("EXPERIMENTAL").assertIsEnabled()
    }
}
