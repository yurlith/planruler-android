package com.planruler.backup

import com.planruler.crm.api.CrmBackupSnapshot
import com.planruler.model.PlanProject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupTrashEntry(
    val project: PlanProject,
    val deletedAtEpochMs: Long,
)

@Serializable
data class PlanRulerBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val createdAtEpochMs: Long,
    val appVersion: String,
    val activeProjects: List<PlanProject>,
    val trash: List<BackupTrashEntry>,
    val crm: CrmBackupSnapshot,
    /** Source PDFs/images referenced by content URI are not duplicated into this compact data backup. */
    val externalDocumentUris: List<String> = activeProjects.map(PlanProject::documentUri).distinct(),
) {
    companion object { const val CURRENT_SCHEMA = 1 }
}

@Serializable
private data class EncryptedEnvelope(
    val magic: String = MAGIC,
    val version: Int = 1,
    val cipher: String = "AES-256-GCM",
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int,
    val saltBase64: String,
    val ivBase64: String,
    val payloadSha256Base64: String,
    val encryptedPayloadBase64: String,
)

class BackupException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object EncryptedBackupCodec {
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(
        payload: PlanRulerBackupPayload,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(password.size >= 8) { "Backup password must contain at least eight characters" }
        val serialized = json.encodeToString(PlanRulerBackupPayload.serializer(), payload)
            .toByteArray(StandardCharsets.UTF_8)
        val compressed = gzip(serialized)
        serialized.fill(0)
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = derive(password, salt, ITERATIONS)
        password.fill('\u0000')
        val encrypted = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(MAGIC.toByteArray(StandardCharsets.US_ASCII))
                doFinal(compressed)
            }
        } finally {
            key.fill(0)
        }
        val envelope = EncryptedEnvelope(
            iterations = ITERATIONS,
            saltBase64 = b64(salt),
            ivBase64 = b64(iv),
            payloadSha256Base64 = b64(MessageDigest.getInstance("SHA-256").digest(compressed)),
            encryptedPayloadBase64 = b64(encrypted),
        )
        compressed.fill(0)
        return json.encodeToString(EncryptedEnvelope.serializer(), envelope)
            .toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray, password: CharArray): PlanRulerBackupPayload {
        val envelope = runCatching {
            json.decodeFromString(EncryptedEnvelope.serializer(), String(bytes, StandardCharsets.UTF_8))
        }.getOrElse { throw BackupException("This is not a PlanRuler backup", it) }
        if (envelope.magic != MAGIC || envelope.version != 1 || envelope.iterations < 100_000) {
            throw BackupException("Unsupported or unsafe PlanRuler backup format")
        }
        val salt = fromB64(envelope.saltBase64)
        val iv = fromB64(envelope.ivBase64)
        val encrypted = fromB64(envelope.encryptedPayloadBase64)
        val key = derive(password, salt, envelope.iterations)
        password.fill('\u0000')
        val compressed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(MAGIC.toByteArray(StandardCharsets.US_ASCII))
                doFinal(encrypted)
            }
        } catch (badPassword: AEADBadTagException) {
            throw BackupException("Wrong password or damaged backup", badPassword)
        } finally {
            key.fill(0)
        }
        val expectedHash = fromB64(envelope.payloadSha256Base64)
        val actualHash = MessageDigest.getInstance("SHA-256").digest(compressed)
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            compressed.fill(0)
            throw BackupException("Backup integrity check failed")
        }
        val plain = runCatching { gunzip(compressed) }
            .getOrElse { throw BackupException("Backup payload is damaged", it) }
        compressed.fill(0)
        return try {
            json.decodeFromString(PlanRulerBackupPayload.serializer(), String(plain, StandardCharsets.UTF_8)).also {
                if (it.schemaVersion !in 1..PlanRulerBackupPayload.CURRENT_SCHEMA) {
                    throw BackupException("Unsupported backup data version ${it.schemaVersion}")
                }
            }
        } finally {
            plain.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun fromB64(value: String): ByteArray = runCatching { Base64.getDecoder().decode(value) }
        .getOrElse { throw BackupException("Backup contains invalid binary data", it) }
}

private const val MAGIC = "PLANRULER-LOCAL-BACKUP"
