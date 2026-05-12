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
import kotlin.test.assertTrue

class SettingsSearchTest {

    private fun createDefinition(id: String, title: String, subtitle: String? = null, section: String = "Section"): SettingDefinition {
        return SettingDefinition.SwitchSetting(
            id = id,
            title = SettingText.Raw(title),
            subtitle = subtitle?.let { SettingText.Raw(it) },
            icon = Icons.Default.Settings,
            sectionTitle = SettingText.Raw(section),
            navKey = SettingNavKey.SystemSettings,
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
}
