package org.wip.plugintoolkit.api

import kotlin.test.Test
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
}
