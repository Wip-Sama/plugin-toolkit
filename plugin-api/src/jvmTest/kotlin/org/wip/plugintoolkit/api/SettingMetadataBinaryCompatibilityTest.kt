package org.wip.plugintoolkit.api

import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertTrue

class SettingMetadataBinaryCompatibilityTest {
    @Test
    fun `retains pre-hints JVM constructor`() {
        val oldParameterTypes = listOf(
            JsonElement::class.java,
            String::class.java,
            DataType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            ParameterConstraints::class.java,
            List::class.java
        )

        assertTrue(
            SettingMetadata::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.toList() == oldParameterTypes
            },
            "SettingMetadata must keep the constructor used by plugins compiled against the previous API"
        )
    }
}
