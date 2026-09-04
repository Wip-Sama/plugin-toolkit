package org.wip.plugintoolkit.features.plugin.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PluginSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val jobManager = mockk<JobManager>(relaxed = true)

    private val settingsState = MutableStateFlow<Map<String, PluginSettingsStore>>(emptyMap())
    private val locksState = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())
    private val jobsState = MutableStateFlow<List<org.wip.plugintoolkit.features.job.model.BackgroundJob>>(emptyList())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { pluginManager.pluginSettingsState } returns settingsState
        every { pluginManager.pluginLocksState } returns locksState
        every { jobManager.jobs } returns jobsState
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testStoreUpdatesWhenPluginSettingsStateEmits() = runTest(testDispatcher) {
        val pkg = "org.example.test"
        val initialStore = PluginSettingsStore(
            settings = mapOf("apiKey" to JsonPrimitive("initial_val"))
        )
        every { pluginManager.loadPluginSettings(pkg) } returns initialStore

        val viewModel = PluginSettingsViewModel(pkg, pluginManager, jobManager)
        advanceUntilIdle()

        assertEquals("initial_val", (viewModel.store.value.settings["apiKey"] as? JsonPrimitive)?.content)

        // Plugin action or background process changes setting and emits to pluginSettingsState
        val updatedStore = PluginSettingsStore(
            settings = mapOf("apiKey" to JsonPrimitive("updated_from_action"))
        )
        settingsState.update { it + (pkg to updatedStore) }
        advanceUntilIdle()

        assertEquals("updated_from_action", (viewModel.store.value.settings["apiKey"] as? JsonPrimitive)?.content)
    }
}
