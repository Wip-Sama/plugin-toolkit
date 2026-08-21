package org.wip.plugintoolkit.features.settings.logic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private class FakeDelayedSettingsPersistence(
        var settings: AppSettings = AppSettings()
    ) : SettingsPersistence {
        var loadDelayAction: (suspend () -> Unit)? = null
        var saveCallCount = 0

        override suspend fun load(): AppSettings {
            loadDelayAction?.invoke()
            return settings
        }

        override suspend fun save(settings: AppSettings) {
            this.settings = settings
            saveCallCount++
        }

        override fun getSettingsDir(): String = ""
        override fun getJobsDir(): String = ""
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testInitialLoad() = runTest {
        val initialSettings = AppSettings(
            extensions = org.wip.plugintoolkit.features.settings.model.ExtensionSettings(
                repositories = listOf(ExtensionRepo(name = "Initial", url = "https://initial.com"))
            )
        )
        val persistence = FakeDelayedSettingsPersistence(initialSettings)
        val repository = SettingsRepository(persistence, backgroundScope)

        assertFalse(repository.isLoaded.value)
        repository.isLoaded.first { it }
        assertTrue(repository.isLoaded.value)

        assertEquals(1, repository.settings.value.extensions.repositories.size)
        assertEquals("Initial", repository.settings.value.extensions.repositories[0].name)
    }

    @Test
    fun testUpdateSettingsBeforeLoadCompletesPreservesUpdates() = runTest {
        val initialSettings = AppSettings(
            extensions = org.wip.plugintoolkit.features.settings.model.ExtensionSettings(
                repositories = listOf(ExtensionRepo(name = "Disk Repo", url = "https://disk.com"))
            )
        )
        val persistence = FakeDelayedSettingsPersistence(initialSettings)

        val repository = SettingsRepository(persistence, backgroundScope)

        // Immediately update settings before persistence has loaded
        repository.updateSettings { current ->
            current.copy(
                extensions = current.extensions.copy(
                    repositories = current.extensions.repositories + ExtensionRepo(
                        name = "New Repo",
                        url = "https://new.com"
                    )
                )
            )
        }

        repository.isLoaded.first { it }

        // Both the disk repo and new repo should be present because the update was applied onto loaded settings
        val repos = repository.settings.value.extensions.repositories
        assertEquals(2, repos.size)
        assertEquals("Disk Repo", repos[0].name)
        assertEquals("New Repo", repos[1].name)
    }

    @Test
    fun testMultipleUpdatesBeforeLoadCompletes() = runTest {
        val persistence = FakeDelayedSettingsPersistence(AppSettings())
        val repository = SettingsRepository(persistence, backgroundScope)

        repository.updateSettings { current ->
            current.copy(
                extensions = current.extensions.copy(
                    repositories = listOf(ExtensionRepo(name = "Repo 1", url = "https://1.com"))
                )
            )
        }

        repository.updateSettings { current ->
            current.copy(
                extensions = current.extensions.copy(
                    repositories = current.extensions.repositories + ExtensionRepo(
                        name = "Repo 2",
                        url = "https://2.com"
                    )
                )
            )
        }

        repository.isLoaded.first { it }

        val repos = repository.settings.value.extensions.repositories
        assertEquals(2, repos.size)
        assertEquals("Repo 1", repos[0].name)
        assertEquals("Repo 2", repos[1].name)
    }
}
