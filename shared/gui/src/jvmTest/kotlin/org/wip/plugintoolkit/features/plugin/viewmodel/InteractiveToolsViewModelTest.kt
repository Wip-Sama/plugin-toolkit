package org.wip.plugintoolkit.features.plugin.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.sdui.PluginUiSessionHandle
import org.wip.plugintoolkit.features.plugin.sdui.PluginUiSessionManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractiveToolsViewModelTest {

    private val pluginManager = mockk<PluginManager>(relaxed = true)
    private val uiSessionManager = mockk<PluginUiSessionManager>(relaxed = true)
    private val notificationService = mockk<NotificationService>(relaxed = true)

    private val standardCap = Capability(
        name = "headlessTask",
        description = "Standard background capability",
        returnType = DataType.Primitive(PrimitiveType.STRING),
        isInteractive = false,
        context = CapabilityContext.ANY
    )

    private val interactiveCap = Capability(
        name = "drawingBoard",
        description = "Interactive vector canvas",
        returnType = DataType.Primitive(PrimitiveType.STRING),
        isInteractive = true,
        context = CapabilityContext.INTERACTIVE_ONLY
    )

    private val testManifest = PluginManifest(
        manifestVersion = "1.0",
        plugin = PluginInfo(
            id = "org.test.interactive",
            name = "Interactive Plugin",
            version = "1.0.0",
            description = "Test plugin"
        ),
        requirements = Requirements(
            minMemoryMb = 64,
            minExecutionTimeMs = 1000
        ),
        capabilities = listOf(standardCap, interactiveCap)
    )

    private val mockProcessor = mockk<InteractiveUiProcessor>(relaxed = true)
    private val mockSessionHandle = mockk<PluginUiSessionHandle>(relaxed = true)
    private val mockPluginContext = mockk<PluginContext>(relaxed = true)

    private val pluginEntry = object : PluginEntry {
        override fun getManifest(): Result<PluginManifest> = Result.success(testManifest)
        override fun getProcessor(): Result<org.wip.plugintoolkit.api.DataProcessor> = Result.failure(UnsupportedOperationException())
        override fun getInteractiveProcessor(): Result<InteractiveUiProcessor>? = Result.success(mockProcessor)
        override fun setDebug(isDebug: Boolean) {}
    }

    @BeforeTest
    fun setup() {
        mockkObject(PluginLoader)
        every { PluginLoader.getPlugins() } returns listOf(pluginEntry)
        every { mockSessionHandle.isActive } returns true
        every { pluginManager.createPluginContext(any(), any(), any(), any(), any(), any(), any()) } returns mockPluginContext
        every {
            uiSessionManager.startSession(any(), any(), any(), any())
        } returns mockSessionHandle
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInteractiveToolsDiscoveryFiltersOutNormalCapabilities() {
        val viewModel = InteractiveToolsViewModel(
            pluginManager = pluginManager,
            uiSessionManager = uiSessionManager,
            notificationService = notificationService
        )

        val tools = viewModel.interactiveTools.value
        assertEquals(1, tools.size)
        assertEquals("drawingBoard", tools.first().capability.name)
        assertTrue(tools.first().capability.isInteractive)
        assertEquals(CapabilityContext.INTERACTIVE_ONLY, tools.first().capability.context)
    }

    @Test
    fun testSelectToolStartsInteractiveSession() {
        val viewModel = InteractiveToolsViewModel(
            pluginManager = pluginManager,
            uiSessionManager = uiSessionManager,
            notificationService = notificationService
        )

        val tool = viewModel.interactiveTools.value.first()
        viewModel.selectTool(tool)

        assertEquals(tool, viewModel.selectedTool.value)
        assertNotNull(viewModel.activeSessionHandle.value)
        verify {
            uiSessionManager.startSession(
                pluginId = "org.test.interactive",
                capabilityName = "drawingBoard",
                context = mockPluginContext,
                processor = mockProcessor
            )
        }
    }

    @Test
    fun testRestartSessionClosesPreviousAndStartsNew() {
        val viewModel = InteractiveToolsViewModel(
            pluginManager = pluginManager,
            uiSessionManager = uiSessionManager,
            notificationService = notificationService
        )

        val tool = viewModel.interactiveTools.value.first()
        viewModel.selectTool(tool)
        viewModel.restartSession()

        verify { mockSessionHandle.cancel(any()) }
        assertNotNull(viewModel.activeSessionHandle.value)
    }

    @Test
    fun testCloseSessionCancelsHandle() {
        val viewModel = InteractiveToolsViewModel(
            pluginManager = pluginManager,
            uiSessionManager = uiSessionManager,
            notificationService = notificationService
        )

        val tool = viewModel.interactiveTools.value.first()
        viewModel.selectTool(tool)
        viewModel.closeSession()

        verify { mockSessionHandle.cancel("Session closed by user") }
        assertNull(viewModel.activeSessionHandle.value)
    }

    @Test
    fun testSearchQueryUpdatesState() {
        val viewModel = InteractiveToolsViewModel(
            pluginManager = pluginManager,
            uiSessionManager = uiSessionManager,
            notificationService = notificationService
        )

        viewModel.updateSearchQuery("drawing")
        assertEquals("drawing", viewModel.searchQuery.value)
    }
}
