package org.wip.plugintoolkit.features.shortcuts.utils

import androidx.compose.ui.input.key.Key
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyMappingTest {

    @Test
    fun testFromComposeKeyStandardLetterKeys() {
        assertEquals(ShortcutKey.A, KeyMapping.fromComposeKey(Key.A))
        assertEquals(ShortcutKey.Z, KeyMapping.fromComposeKey(Key.Z))
        assertEquals(ShortcutKey.M, KeyMapping.fromComposeKey(Key.M))
    }

    @Test
    fun testFromComposeKeyDigitsAndFunctionKeys() {
        assertEquals(ShortcutKey.Zero, KeyMapping.fromComposeKey(Key.Zero))
        assertEquals(ShortcutKey.Nine, KeyMapping.fromComposeKey(Key.Nine))
        assertEquals(ShortcutKey.F1, KeyMapping.fromComposeKey(Key.F1))
        assertEquals(ShortcutKey.F12, KeyMapping.fromComposeKey(Key.F12))
    }

    @Test
    fun testFromComposeKeyNavigationAndSpecialKeys() {
        assertEquals(ShortcutKey.Escape, KeyMapping.fromComposeKey(Key.Escape))
        assertEquals(ShortcutKey.Enter, KeyMapping.fromComposeKey(Key.Enter))
        assertEquals(ShortcutKey.Delete, KeyMapping.fromComposeKey(Key.Delete))
        assertEquals(ShortcutKey.Backspace, KeyMapping.fromComposeKey(Key.Backspace))
        assertEquals(ShortcutKey.Space, KeyMapping.fromComposeKey(Key.Spacebar))
        assertEquals(ShortcutKey.ArrowUp, KeyMapping.fromComposeKey(Key.DirectionUp))
        assertEquals(ShortcutKey.ArrowDown, KeyMapping.fromComposeKey(Key.DirectionDown))
        assertEquals(ShortcutKey.ArrowLeft, KeyMapping.fromComposeKey(Key.DirectionLeft))
        assertEquals(ShortcutKey.ArrowRight, KeyMapping.fromComposeKey(Key.DirectionRight))
    }

    @Test
    fun testToComposeKeyBidirectional() {
        assertEquals(Key.A, KeyMapping.toComposeKey(ShortcutKey.A))
        assertEquals(Key.Enter, KeyMapping.toComposeKey(ShortcutKey.Enter))
        assertEquals(Key.Escape, KeyMapping.toComposeKey(ShortcutKey.Escape))
        assertEquals(Key.F5, KeyMapping.toComposeKey(ShortcutKey.F5))
    }

    @Test
    fun testFromComposeKeyFallbackForDynamicKey() {
        val customKey = Key(999999999)
        val mapped = KeyMapping.fromComposeKey(customKey)
        assertNotNull(mapped)
        assertTrue(mapped.code.isNotEmpty())
    }

    @Test
    fun testCommonKeysContainExpectedEntries() {
        val keys = KeyMapping.commonKeys
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.contains(ShortcutKey.A))
        assertTrue(keys.contains(ShortcutKey.Enter))
        assertTrue(keys.contains(ShortcutKey.Escape))
        assertTrue(keys.contains(ShortcutKey.Delete))
    }
}
