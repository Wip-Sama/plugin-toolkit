package org.wip.plugintoolkit.features.plugin.logic

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginSecurityTest {

    @Test
    fun testVerifyDetached() {
        // 1. Generate a key pair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)

        // 2. Create data and signature
        val data = "Hello, Plugin Toolkit!"
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(kp.private)
        sig.update(data.toByteArray())
        val signatureBase64 = Base64.getEncoder().encodeToString(sig.sign())

        // 3. Verify - Should be true
        assertTrue(PluginSecurity.verifyDetached(data, signatureBase64, publicKeyBase64))

        // 4. Verify with modified data - Should be false
        assertFalse(PluginSecurity.verifyDetached(data + "modified", signatureBase64, publicKeyBase64))

        // 5. Verify with mismatched key - Should be false
        val otherKp = kpg.generateKeyPair()
        val otherPublicKeyBase64 = Base64.getEncoder().encodeToString(otherKp.public.encoded)
        assertFalse(PluginSecurity.verifyDetached(data, signatureBase64, otherPublicKeyBase64))
        
        // 6. Malformed base64 signature - Should be false
        assertFalse(PluginSecurity.verifyDetached(data, "invalid-base64", publicKeyBase64))
    }

    @Test
    fun testVerifyJarWithInvalidInputs() {
        // 1. Non-existent file
        assertFalse(PluginSecurity.verify("/path/to/nothing.jar", "any-key"))

        // 2. Not a JAR file (plain text file)
        val tempFile = File.createTempFile("not-a-jar", ".txt")
        tempFile.writeText("This is just a text file.")
        try {
            assertFalse(PluginSecurity.verify(tempFile.absolutePath, "any-key"))
        } finally {
            tempFile.delete()
        }

        // 3. Unsigned JAR file
        val unsignedJar = File.createTempFile("unsigned", ".jar")
        ZipOutputStream(FileOutputStream(unsignedJar)).use { zos ->
            val entry = ZipEntry("test.txt")
            zos.putNextEntry(entry)
            zos.write("test content".toByteArray())
            zos.closeEntry()
        }
        try {
            // Generate a random key to test against
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            val publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)
            
            assertFalse(PluginSecurity.verify(unsignedJar.absolutePath, publicKeyBase64))
        } finally {
            unsignedJar.delete()
        }
    }
}
