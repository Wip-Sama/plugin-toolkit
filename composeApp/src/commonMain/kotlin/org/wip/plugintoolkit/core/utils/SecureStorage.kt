package org.wip.plugintoolkit.core.utils

/**
 * Provides access to platform-specific secure storage mechanisms (e.g., DPAPI on Windows,
 * Keychain on macOS, Secret Service on Linux) to encrypt and decrypt sensitive strings.
 */
expect object SecureStorage {
    /**
     * Encrypts the given clear-text string using the OS's secure storage.
     * The encrypted output is usually encoded as a base64 string or similar safe format.
     */
    fun encrypt(clearText: String): String

    /**
     * Decrypts the given encrypted string back to clear-text.
     */
    fun decrypt(encryptedText: String): String
}
