package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PluginSettingsLayoutRegressionTest {

    private lateinit var mockManager: PluginManager
    private lateinit var mockJobManager: JobManager

    @Before
    fun setUp() {
        mockManager = io.mockk.mockk<PluginManager>(relaxed = true)
        mockJobManager = io.mockk.mockk<JobManager>(relaxed = true)

        io.mockk.every { mockManager.pluginLocksState } returns MutableStateFlow(emptyMap())
        io.mockk.every { mockManager.pluginSettingsState } returns MutableStateFlow(emptyMap())
        io.mockk.every { mockManager.loadedPlugins } returns MutableStateFlow(setOf("org.wip.math"))
        io.mockk.every { mockJobManager.jobs } returns MutableStateFlow(emptyList())

        startKoin {
            modules(module {
                single { mockManager }
                single { mockJobManager }
                val semanticRegistryMock = io.mockk.mockk<SemanticRegistry>(relaxed = true)
                single { semanticRegistryMock }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testNavigationSidebar_withBlankTitleAndCannotCollapse_doesNotRenderEmptyHeaderTitle() = runDesktopComposeUiTest {
        setContent {
            NavigationSidebar(
                title = "".localized,
                bodySections = listOf(
                    SidebarSectionData(
                        title = "Settings".localized,
                        elements = listOf(
                            SidebarElement(
                                id = "custom",
                                icon = Icons.Default.Settings,
                                title = "Custom Settings".localized
                            )
                        )
                    )
                ),
                currentScreen = "custom",
                onScreenSelected = {},
                isNavbarCollapsed = false,
                onToggleNavbar = {},
                canCollapse = false
            )
        }

        // Section header and element should be present
        onNodeWithText("Settings").assertExists()
        onNodeWithText("Custom Settings").assertExists()
    }

    @Test
    fun testPluginManagerScreen_dataClassEqualityWithParameters() {
        val screen1 = Screen.PluginManager("org.wip.math", "serverToken")
        val screen2 = Screen.PluginManager("org.wip.math", "serverToken")
        val screen3 = Screen.PluginManager()

        assertEquals(screen1, screen2)
        assertEquals("org.wip.math", screen1.pluginId)
        assertEquals("serverToken", screen1.scrollToSetting)
        assertEquals(null, screen3.pluginId)
        assertEquals(null, screen3.scrollToSetting)
    }
}
