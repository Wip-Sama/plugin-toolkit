package org.wip.minimal

import kotlin.test.Test
import kotlin.test.assertEquals

class MinimalPluginTest {

    @Test
    fun testGreetDefault() {
        val plugin = MinimalPlugin()
        assertEquals("Hello, World!", plugin.greet("World"))
    }

    @Test
    fun testGreetCustomName() {
        val plugin = MinimalPlugin()
        assertEquals("Hello, Alice!", plugin.greet("Alice"))
    }

    @Test
    fun testGreetEnthusiastic() {
        val plugin = MinimalPlugin()
        assertEquals("Hello, Bob!!!", plugin.greet("Bob", enthusiastic = true))
    }
}
