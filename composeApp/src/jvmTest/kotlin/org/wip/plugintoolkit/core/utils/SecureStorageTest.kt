package org.wip.plugintoolkit.core.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecureStorageTest {

    @Test
    fun testEncryptDecrypt() {
        val clearText = "superSecretPassword123!"
        
        val encrypted = SecureStorage.encrypt(clearText)
        
        // It shouldn't be the same
        assertNotEquals(clearText, encrypted)
        
        // It should start with a known prefix
        assertTrue(encrypted.startsWith("dpapi:") || encrypted.startsWith("aes:"))
        
        val decrypted = SecureStorage.decrypt(encrypted)
        
        // Should decrypt back to the original
        assertEquals(clearText, decrypted)
    }

    @Test
    fun testEmptyString() {
        val clearText = ""
        val encrypted = SecureStorage.encrypt(clearText)
        assertEquals("", encrypted)
        
        val decrypted = SecureStorage.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun testUnencryptedFallback() {
        // If an unencrypted string without a prefix is passed, it should return it as is
        val text = "not_encrypted"
        val decrypted = SecureStorage.decrypt(text)
        assertEquals(text, decrypted)
    }
}
