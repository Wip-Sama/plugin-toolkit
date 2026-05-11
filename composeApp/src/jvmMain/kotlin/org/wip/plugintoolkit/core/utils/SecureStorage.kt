package org.wip.plugintoolkit.core.utils

import co.touchlab.kermit.Logger
import com.sun.jna.platform.win32.Crypt32Util
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

actual object SecureStorage {

    actual fun encrypt(clearText: String): String {
        if (clearText.isEmpty()) return clearText
        return try {
            when {
                PlatformUtils.isWindows -> {
                    val encryptedBytes = Crypt32Util.cryptProtectData(clearText.toByteArray(StandardCharsets.UTF_8))
                    "dpapi:" + Base64.getEncoder().encodeToString(encryptedBytes)
                }
                PlatformUtils.isMac -> {
                    // macOS doesn't have a simple generic encrypt-only data API via CLI like dpapi.
                    // Instead of storing it in the keychain and returning a key, we can use a machine-derived key.
                    // If we wanted to use keychain, we'd need to manage service/account names per setting.
                    "aes:" + fallbackEncrypt(clearText)
                }
                PlatformUtils.isLinux -> {
                    "aes:" + fallbackEncrypt(clearText)
                }
                else -> "aes:" + fallbackEncrypt(clearText)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to encrypt data, using fallback" }
            "aes:" + fallbackEncrypt(clearText)
        }
    }

    actual fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty() || !encryptedText.contains(":")) return encryptedText
        val parts = encryptedText.split(":", limit = 2)
        val prefix = parts[0]
        val payload = parts[1]

        return try {
            when (prefix) {
                "dpapi" -> {
                    if (PlatformUtils.isWindows) {
                        val encryptedBytes = Base64.getDecoder().decode(payload)
                        val clearBytes = Crypt32Util.cryptUnprotectData(encryptedBytes)
                        String(clearBytes, StandardCharsets.UTF_8)
                    } else {
                        throw IllegalStateException("Cannot decrypt DPAPI on non-Windows platform")
                    }
                }
                "aes" -> {
                    fallbackDecrypt(payload)
                }
                else -> encryptedText // Not encrypted or unknown prefix
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to decrypt data" }
            ""
        }
    }

    private fun getMachineId(): String {
        // A simple machine ID derivation to use as an AES key for platforms where we don't have DPAPI
        val userName = System.getProperty("user.name") ?: "unknown"
        val osVersion = System.getProperty("os.version") ?: "unknown"
        // Try to get MAC address or something hardware specific, but for simplicity:
        return "$userName-$osVersion-plugintoolkit"
    }

    private fun getAESKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(getMachineId().toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun fallbackEncrypt(clearText: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, getAESKey())
        val encrypted = cipher.doFinal(clearText.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun fallbackDecrypt(encryptedText: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, getAESKey())
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText))
        return String(decrypted, StandardCharsets.UTF_8)
    }
}
