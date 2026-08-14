package org.wip.plugintoolkit.features.plugin.ui

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SettingMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginSettingPartitionTest {
    @Test
    fun `partitions required and optional settings while preserving order`() {
        val settings = linkedMapOf(
            "optionalFirst" to setting(required = false),
            "requiredFirst" to setting(required = true),
            "requiredSecond" to setting(required = true),
            "optionalSecond" to setting(required = false)
        )

        val (required, optional) = partitionSettings(settings)

        assertEquals(listOf("requiredFirst", "requiredSecond"), required.keys.toList())
        assertEquals(listOf("optionalFirst", "optionalSecond"), optional.keys.toList())
        assertEquals(settings.keys, (required.keys + optional.keys).toSet())
    }

    @Test
    fun `empty settings produce two empty groups`() {
        val (required, optional) = partitionSettings(emptyMap())

        assertTrue(required.isEmpty())
        assertTrue(optional.isEmpty())
    }

    private fun setting(required: Boolean) = SettingMetadata(
        description = "Setting",
        type = DataType.Primitive(PrimitiveType.STRING),
        required = required
    )
}
