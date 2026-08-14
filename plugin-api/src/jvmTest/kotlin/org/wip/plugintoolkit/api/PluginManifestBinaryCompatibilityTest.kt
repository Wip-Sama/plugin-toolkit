package org.wip.plugintoolkit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginManifestBinaryCompatibilityTest {
    @Test
    fun `retains constructors used before plugin UI pages`() {
        val constructors = PluginManifest::class.java.declaredConstructors.map { constructor ->
            constructor.parameterTypes.toList()
        }

        assertTrue(constructors.any { it.size == 11 && it.last() == Boolean::class.javaPrimitiveType })
        assertTrue(constructors.any {
            it.size == 13 &&
                it[it.lastIndex - 1] == Int::class.javaPrimitiveType &&
                it.last().name == "kotlin.jvm.internal.DefaultConstructorMarker"
        })
    }

    @Test
    fun `retains copy bridges used before plugin UI pages`() {
        val methods = PluginManifest::class.java.declaredMethods
        val oldCopy = methods.single { it.name == "copy" && it.parameterCount == 11 }
        val oldDefaultCopy = methods.single { it.name == "copy\$default" && it.parameterCount == 14 }
        val original = PluginManifest(
            manifestVersion = "1",
            plugin = PluginInfo("id", "name", "1", "description"),
            requirements = Requirements(1, 1),
            uiPages = listOf(PluginUiPage("page", "Page"))
        )

        val copied = oldDefaultCopy.invoke(
            null, original, null, null, null, null, null, null, null, null,
            false, false, false, 0x7FF, null
        ) as PluginManifest

        assertEquals(original, copied)
        assertEquals(PluginManifest::class.java, oldCopy.returnType)
    }
}
