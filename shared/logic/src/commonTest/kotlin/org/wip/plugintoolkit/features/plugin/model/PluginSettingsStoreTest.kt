package org.wip.plugintoolkit.features.plugin.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginSettingsStoreTest {

    @Test
    fun testCalculateEffectiveMaxConcurrentWhenDeveloperDefinesLimit() {
        // Dev max = 4, user sets lower limit = 2 -> user limit applied
        assertEquals(2, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = 4, userConfigured = 2))

        // Dev max = 4, user attempts higher limit = 6 -> capped at dev max 4
        assertEquals(4, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = 4, userConfigured = 6))

        // Dev max = 4, user sets equal limit = 4 -> 4
        assertEquals(4, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = 4, userConfigured = 4))

        // Dev max = 4, user has not set a limit (null or 0) -> defaults to dev max 4
        assertEquals(4, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = 4, userConfigured = null))
        assertEquals(4, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = 4, userConfigured = 0))
    }

    @Test
    fun testCalculateEffectiveMaxConcurrentWhenDeveloperHasNoLimit() {
        // Dev max = null / 0, user sets limit = 3 -> 3
        assertEquals(3, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = null, userConfigured = 3))
        assertEquals(3, PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = 0, userConfigured = 3))

        // Dev max = null / 0, user has no limit -> null
        assertNull(PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = null, userConfigured = null))
        assertNull(PluginSettingsStore.calculateEffectiveMaxConcurrent(developerMax = null, userConfigured = 0))
    }

    @Test
    fun testStoreGetEffectiveMaxConcurrentMethod() {
        val store = PluginSettingsStore(
            capabilityMaxConcurrency = mapOf(
                "capLimitedByUser" to 2,
                "capExcessiveUser" to 10
            )
        )

        // Dev max 5, user set 2 -> 2
        assertEquals(2, store.getEffectiveMaxConcurrent("capLimitedByUser", developerMax = 5))

        // Dev max 3, user set 10 -> capped at 3
        assertEquals(3, store.getEffectiveMaxConcurrent("capExcessiveUser", developerMax = 3))

        // Dev max 5, no user setting -> 5
        assertEquals(5, store.getEffectiveMaxConcurrent("capUnsetByUser", developerMax = 5))

        // No dev max, no user setting -> null
        assertNull(store.getEffectiveMaxConcurrent("capUnsetByUser", developerMax = null))
    }
}
