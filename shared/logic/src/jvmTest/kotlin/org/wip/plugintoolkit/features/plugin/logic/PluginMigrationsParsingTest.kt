package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginMigrationsParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testParsePluginMigrationsArray() {
        val raw = """
            [
              {
                "fromVersion": "2.8.0",
                "toVersion": "2.9.0",
                "capabilityMigrations": []
              }
            ]
        """.trimIndent()
        val result = PluginLifecycleManager.parsePluginMigrationsJson(raw, json)
        assertEquals(1, result.size)
        assertEquals("2.8.0", result.first().fromVersion)
        assertEquals("2.9.0", result.first().toVersion)
    }

    @Test
    fun testParseSinglePluginMigrationObject() {
        val raw = """
            {
              "fromVersion": "2.8.0",
              "toVersion": "3.0.0",
              "settingMigrations": [
                { "oldName": "oldKey", "newName": "newKey" }
              ]
            }
        """.trimIndent()
        val result = PluginLifecycleManager.parsePluginMigrationsJson(raw, json)
        assertEquals(1, result.size)
        assertEquals("2.8.0", result.first().fromVersion)
        assertEquals("3.0.0", result.first().toVersion)
        assertEquals("newKey", result.first().settingMigrations.first().newName)
    }

    @Test
    fun testParseWrappedMigrationsObject() {
        val raw = """
            {
              "migrations": [
                { "fromVersion": "1.0.0", "toVersion": "1.1.0" },
                { "fromVersion": "1.1.0", "toVersion": "1.2.0" }
              ]
            }
        """.trimIndent()
        val result = PluginLifecycleManager.parsePluginMigrationsJson(raw, json)
        assertEquals(2, result.size)
        assertEquals("1.2.0", result[1].toVersion)
    }
}
