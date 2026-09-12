package org.wip.plugintoolkit.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsSearchViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchTest {

    private fun createDefinition(
        id: String,
        title: String,
        subtitle: String? = null,
        section: String = "Section",
        navKey: SettingNavKey = SettingNavKey.SystemSettings
    ): SettingDefinition {
        return SettingDefinition.SwitchSetting(
            id = id,
            title = SettingText.Raw(title),
            subtitle = subtitle?.let { SettingText.Raw(it) },
            icon = Icons.Default.Settings,
            sectionTitle = SettingText.Raw(section),
            navKey = navKey,
            getValue = { false },
            setValue = { s, _ -> s }
        )
    }

    @Test
    fun testModularRegistryAndSearch() {
        val defs = listOf(
            createDefinition("s1", "Auto Update", "Enable automatic updates"),
            createDefinition("s2", "Log Level", "Choose log level"),
            createDefinition("s3", "Theme", "Dark or Light")
        )

        val registry = SettingsRegistry(defs)
        val viewModel = SettingsSearchViewModel(registry)

        val resolvedStrings = registry.definitions.value.flatMap {
            listOfNotNull(it.title, it.subtitle, it.sectionTitle)
        }.associateWith { (it as SettingText.Raw).text }

        // Test 1: Search for "Update"
        viewModel.searchQuery = "Update"
        val results = viewModel.getBroadSearchResults(registry.definitions.value, resolvedStrings)
        assertEquals(1, results.values.flatten().size)
        assertEquals("s1", results.values.flatten().first().id)

        // Test 2: Search for "Log"
        viewModel.searchQuery = "Log"
        val results2 = viewModel.getBroadSearchResults(registry.definitions.value, resolvedStrings)
        assertEquals(1, results2.values.flatten().size)
        assertEquals("s2", results2.values.flatten().first().id)

        // Test 3: Search for "Dark" (matches subtitle of Theme)
        viewModel.searchQuery = "Dark"
        val results3 = viewModel.getBroadSearchResults(registry.definitions.value, resolvedStrings)
        assertEquals(1, results3.values.flatten().size)
        assertEquals("s3", results3.values.flatten().first().id)

        // Test 4: Blank search returns all
        viewModel.searchQuery = ""
        val results4 = viewModel.getBroadSearchResults(registry.definitions.value, resolvedStrings)
        assertEquals(3, results4.values.flatten().size)
    }

    @Test
    fun testNotificationHistoryExcludedFromBroadSearch() {
        val defs = listOf(
            createDefinition("s1", "System setting", "System subtitle", navKey = SettingNavKey.SystemSettings),
            createDefinition("s2", "History setting", "History subtitle", navKey = SettingNavKey.NotificationHistory)
        )

        val registry = SettingsRegistry(defs)
        val viewModel = SettingsSearchViewModel(registry)
        val resolvedStrings = registry.definitions.value.flatMap {
            listOfNotNull(it.title, it.subtitle, it.sectionTitle)
        }.associateWith { (it as SettingText.Raw).text }

        viewModel.searchQuery = "History"
        val results = viewModel.getBroadSearchResults(registry.definitions.value, resolvedStrings)
        // History setting should be excluded from search pool
        assertEquals(0, results.values.flatten().size)
    }

    @Test
    fun testHasLocalMatches() {
        val defs = listOf(
            createDefinition("s1", "Auto Update", "Enable automatic updates", navKey = SettingNavKey.SystemSettings)
        )
        val registry = SettingsRegistry(defs)
        val viewModel = SettingsSearchViewModel(registry)
        val resolvedStrings = registry.definitions.value.flatMap {
            listOfNotNull(it.title, it.subtitle, it.sectionTitle)
        }.associateWith { (it as SettingText.Raw).text }

        viewModel.searchQuery = "Auto"
        assertTrue(viewModel.hasLocalMatches(SettingNavKey.SystemSettings, registry.definitions.value, resolvedStrings))

        viewModel.searchQuery = "NonExistent"
        assertFalse(viewModel.hasLocalMatches(SettingNavKey.SystemSettings, registry.definitions.value, resolvedStrings))

        // BroadSearch always returns true
        assertTrue(viewModel.hasLocalMatches(SettingNavKey.BroadSearch, registry.definitions.value, resolvedStrings))
    }

    @Test
    fun testBroadSearchIncludesShortcuts() {
        val registry = SettingsRegistry(emptyList())
        val viewModel = SettingsSearchViewModel(registry)

        // Search for "Pan" - should match FLOW_PAN_CANVAS shortcut
        viewModel.searchQuery = "Pan"
        val results = viewModel.getBroadSearchResults(emptyList(), emptyMap())
        val items = results.values.flatten()
        assertTrue(items.any { it.id == "shortcut.flow.board.pan" })

        // Search for "Drag" - matches gestures
        viewModel.searchQuery = "Drag"
        val results2 = viewModel.getBroadSearchResults(emptyList(), emptyMap())
        val items2 = results2.values.flatten()
        assertTrue(items2.isNotEmpty())
    }
}
