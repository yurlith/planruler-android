package com.planruler.crm.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

interface FieldCipher {
    fun encrypt(profileId: String, plainText: String): String
    fun decrypt(profileId: String, encoded: String): String
    /** Removes the profile's Keystore key so it does not accumulate after the profile is gone. */
    fun deleteKey(profileId: String)
}

internal class KeystoreFieldCipher : FieldCipher {
    private val random = SecureRandom()

    override fun encrypt(profileId: String, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(profileId))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return "v1:${b64(cipher.iv)}:${b64(encrypted)}"
    }

    override fun decrypt(profileId: String, encoded: String): String {
        val parts = encoded.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Unsupported encrypted CRM field" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(profileId), GCMParameterSpec(128, fromB64(parts[1])))
        return String(cipher.doFinal(fromB64(parts[2])), StandardCharsets.UTF_8)
    }

    override fun deleteKey(profileId: String) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.deleteEntry(alias(profileId))
    }

    private fun alias(profileId: String) = "planruler.crm.${profileId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}".take(120)

    private fun key(profileId: String): java.security.Key {
        val alias = alias(profileId)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(alias, null)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
            random,
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun fromB64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object { const val TRANSFORMATION = "AES/GCM/NoPadding" }
}

internal data class PinVerifier(val saltBase64: String, val hashBase64: String, val iterations: Int)

internal object PinSecurity {
    private const val DEFAULT_ITERATIONS = 180_000
    private const val KEY_BITS = 256

    fun create(pin: CharArray, random: SecureRandom = SecureRandom()): PinVerifier {
        require(pin.size >= 4) { "PIN must contain at least four characters" }
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = derive(pin, salt, DEFAULT_ITERATIONS)
        pin.fill('\u0000')
        return PinVerifier(b64(salt), b64(hash), DEFAULT_ITERATIONS)
    }

    fun verify(pin: CharArray, verifier: PinVerifier): Boolean {
        val actual = derive(pin, fromB64(verifier.saltBase64), verifier.iterations)
        pin.fill('\u0000')
        return MessageDigest.isEqual(actual, fromB64(verifier.hashBase64))
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations >= 100_000) { "Unsafe PIN verifier" }
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun fromB64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
