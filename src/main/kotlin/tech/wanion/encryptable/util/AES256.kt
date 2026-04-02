package tech.wanion.encryptable.util

import org.slf4j.LoggerFactory
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.AES256.GCM_IV_LENGTH
import tech.wanion.encryptable.util.extensions.clear
import tech.wanion.encryptable.util.extensions.decode64
import tech.wanion.encryptable.util.extensions.encode64
import tech.wanion.encryptable.util.extensions.markForWiping
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility object for AES-256 encryption and decryption using GCM mode.
 *
 * Features:
 * - AES-256 encryption/decryption using GCM (Galois/Counter Mode) for authenticated encryption.
 * - Key derivation from a secret string using HKDF for strong, unique keys per source.
 * - Secure IV (Initialization Vector) generation for each encryption operation.
 * - Encrypts and decrypts both String and ByteArray data, with automatic Base64 encoding/decoding for strings.
 * - Prepends IV to ciphertext for safe transmission and storage.
 * - Silent failure with audit logging: Errors are logged internally without exposing cryptographic details to callers.
 *
 * Usage:
 * - Use `encrypt(secret, source, data)` to encrypt a String or ByteArray. For strings, the result is Base64-encoded.
 * - Use `decrypt(secret, source, data)` to decrypt a Base64 string or ByteArray. For strings, the input must be Base64-encoded.
 * - The IV is generated automatically and included in the output; it is extracted during decryption.
 * - The secret should be high-entropy and unique per user or object for best security.
 *
 * Implementation Notes:
 * - Uses HKDF for key derivation, ensuring keys are unique per secret and source.
 * - Uses AES-GCM for encryption, providing confidentiality and integrity.
 * - All cryptographic operations use SecureRandom for IV generation.
 * - Errors are logged for audit purposes but not re-thrown to prevent information leakage.
 * - The class is stateless and thread-safe.
 * - Encrypted data format: [12-byte IV][ciphertext][16-byte authentication tag]
 * - Minimum encrypted data size: 12 + 1 + 16 = 29 bytes (IV + 1 byte plaintext + tag)
 *
 * JCA Provider Strategy:
 * - **No explicit provider is specified** — `Cipher.getInstance(AES_ALGORITHM_GCM)` lets the JVM select the best available provider.
 * - This design enables hardware-accelerated cryptography when available:
 *   - ARM Crypto Extensions (ARM Cortex-A57+, OCI Ampere, AWS Graviton instances)
 *   - Intel AES-NI (modern x86/x64 processors)
 *   - Other platform-specific accelerators
 * - Frameworks should never force a specific provider, as this bypasses hardware optimizations and degrades performance.
 *
 * Security Considerations:
 * - This class is designed to minimize the exposure of sensitive data in memory.
 * - Secrets and decrypted data are marked for wiping using `markForWiping` to reduce the risk of memory scraping attacks.
 * - It is recommended to use high-entropy, unique secrets for each encryption operation.
 * - GCM nonce (IV) reuse with the same key is cryptographically catastrophic; this implementation uses random IVs.
 * - Decryption failures (authentication tag mismatch, invalid input) are handled silently and return the original ciphertext.
 *
 * Thread Safety:
 * - This object is stateless and fully thread-safe.
 * - `SecureRandom` instance is thread-safe for concurrent IV generation.
 * - Each encryption operation generates a fresh IV, preventing nonce reuse across threads.
 */
object AES256 {
    /** Logger instance for audit logging. */
    private val logger = LoggerFactory.getLogger(AES256::class.java)

    /** SecureRandom instance for generating cryptographically strong random values. */
    private val random = SecureRandom()

    /** AES encryption algorithm identifier. */
    private const val AES_ALGORITHM: String = "AES"
    private const val AES_ALGORITHM_GCM: String = "AES/GCM/NoPadding"

    /** Length of the Initialization Vector (IV) for AES-GCM in bytes. */
    private const val GCM_IV_LENGTH: Int = 12
    /** Length of the authentication tag for AES-GCM in bytes. */
    private const val TAG_LENGTH_BYTES: Int = 16

    /** Total overhead added by AES-GCM encryption (IV + authentication tag) in bytes. */
    const val GCM_OVERHEAD_BYTES: Int = GCM_IV_LENGTH + TAG_LENGTH_BYTES

    /** Helper to get a source name. */
    private fun sourceNameFor(source: Any): String = (source as? Class<*>)?.simpleName ?: source::class.java.simpleName

    /**
     * Generates a cryptographically secure Initialization Vector (IV) for AES-GCM.
     *
     * Memory Exposure Mitigation:
     * - The IV is not a secret and does not require clearing.
     * - No sensitive data is exposed in memory by this method.
     *
     * @return A cryptographically secure random byte array of [GCM_IV_LENGTH] length.
     */
    @JvmStatic
    fun generateIv(): ByteArray = ByteArray(GCM_IV_LENGTH).apply { random.nextBytes(this) }

    /**
     * Derives an AES key from a given secret string using HKDF.
     *
     * Memory Exposure Mitigation:
     * - The derived key and the input secret are registered for clearing to minimize memory exposure.
     * - The returned SecretKeySpec is cleared after use in encryption/decryption methods.
     * - JVM limitations: Complete removal of all copies cannot be guaranteed due to garbage collection and String immutability.
     *
     * @param secret The secret string to use for key generation.
     * @param source The context for key derivation (e.g., entity class).
     * @return A [SecretKeySpec] for AES, suitable for use in a `Cipher` instance.
     *
     */
    @JvmStatic
    fun generateAesKeyFromSecret(secret: String, source: Any): SecretKeySpec {
        val key = HKDF.deriveFromEntropy(secret, source, context = "ENCRYPTION_KEY")
        // Register both the raw key bytes and the secret for end-of-request wiping.
        // `markForWiping` uses a Set internally — registering the same instance twice is idempotent.
        markForWiping(secret, key)
        return SecretKeySpec(key, AES_ALGORITHM)
    }

    /**
     * Encrypts a plaintext string using AES-GCM and returns the result as a Base64-encoded string.
     *
     * Memory Exposure Mitigation:
     * - The input secret and plaintext are registered for clearing to minimize memory exposure.
     * - The resulting ciphertext is not considered sensitive after encryption.
     * - JVM limitations: Complete removal of all copies cannot be guaranteed due to garbage collection and String immutability.
     *
     * @param secret The secret string used to derive the encryption key.
     * @param source The context for key derivation (e.g., entity class).
     * @param data The plaintext string to be encrypted.
     * @return The Base64-encoded string containing the combined IV and ciphertext.
     */
    @JvmStatic
    fun encrypt(secret: String, source: Any, data: String): String {
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        // Register for clearing to minimize memory exposure
        markForWiping(secret, dataBytes)
        return encrypt(secret, source, dataBytes).encode64()
    }

    /**
     * Decrypts a Base64-encoded string containing AES-GCM encrypted data.
     *
     * Memory Exposure Mitigation:
     * - The input secret, decrypted bytes, and resulting plaintext are registered for clearing to minimize memory exposure.
     * - JVM limitations: Complete removal of all copies cannot be guaranteed due to garbage collection and String immutability.
     * - For ultra-high-security, consider hardware memory enclaves.
     *
     * @param secret The secret string used to derive the decryption key.
     * @param source The context for key derivation (e.g., entity class).
     * @param data The Base64-encoded string containing the IV and ciphertext.
     * @return The decrypted plaintext string.
     */
    @JvmStatic
    fun decrypt(secret: String, source: Any, data: String): String {
        val decryptedBytes = decrypt(secret, source, data.decode64())
        val decodedString = decryptedBytes.decodeToString()
        // Register for clearing to minimize memory exposure
        markForWiping(secret, decryptedBytes, decodedString)
        return decodedString
    }

    /**
     * Encrypts a byte array using AES-GCM.
     *
     * Memory Exposure Mitigation:
     * - The input secret and plaintext byte array are registered for clearing to minimize memory exposure.
     * - The derived key is cleared after use.
     * - The resulting ciphertext is not considered sensitive after encryption.
     * - JVM limitations: Complete removal of all copies cannot be guaranteed due to garbage collection.
     *
     * @param secret The secret string used to derive the encryption key.
     * @param source The context for key derivation (e.g., entity class).
     * @param data The byte array to be encrypted.
     * @return The combined byte array containing the IV and the ciphertext, or an empty array on failure.
     */
    fun encrypt(secret: String, source: Any, data: ByteArray): ByteArray {
        // Validate input
        require(secret.isNotBlank()) { "Secret cannot be blank" }
        
        // Register for clearing to minimize memory exposure
        markForWiping(secret, data)
        val aesKey = generateAesKeyFromSecret(secret, source)
        val iv = generateIv()
        val cipher = Cipher.getInstance(AES_ALGORITHM_GCM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BYTES * 8, iv)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
            val encryptedData = ByteArray(data.size + GCM_OVERHEAD_BYTES)
            // Prepend IV to the output array
            System.arraycopy(iv, 0, encryptedData, 0, GCM_IV_LENGTH)
            cipher.doFinal(data, 0, data.size, encryptedData, GCM_IV_LENGTH)
            return encryptedData
        } catch (e: Exception) {
            val sourceName = sourceNameFor(source)
            logger.error("Encryption failed - Source: $sourceName, DataSize: ${data.size} bytes, Error: ${e.javaClass.simpleName}")
            if (source is Encryptable<*>)
                Encryptable.setErrored(source)
            return ByteArray(0)
        } finally {
            // Immediately zero the AES key — not deferred. `markForWiping` is end-of-request;
            // `aesKey.clear()` wipes the key bytes right here, regardless of success or failure.
            aesKey.clear()
        }
    }

    /**
     * Decrypts a byte array containing combined IV and ciphertext using AES-GCM.
     *
     * Memory Exposure Mitigation:
     * - The input secret, derived key, and decrypted byte array are registered for clearing to minimize memory exposure.
     * - The derived key is cleared after use.
     * - JVM limitations: Complete removal of all copies cannot be guaranteed due to garbage collection.
     * - For ultra-high-security, consider hardware memory enclaves.
     *
     * @param secret The secret string used to derive the decryption key.
     * @param source The context for key derivation (e.g., entity class).
     * @param data The combined byte array containing the IV and ciphertext.
     * @return The decrypted byte array, or the same `data` on failure.
     */
    fun decrypt(secret: String, source: Any, data: ByteArray): ByteArray {
        // Validate input
        require(secret.isNotBlank()) { "Secret cannot be blank" }
        require(data.size > GCM_IV_LENGTH) { "Encrypted data too short (minimum ${GCM_IV_LENGTH + 1} bytes, got ${data.size})" }

        val aesKey = generateAesKeyFromSecret(secret, source)
        val iv = ByteArray(GCM_IV_LENGTH)
        // Extract IV from the beginning of the input data
        System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH)
        val dataLength = data.size - GCM_IV_LENGTH
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BYTES * 8, iv)
        val cipher = Cipher.getInstance(AES_ALGORITHM_GCM)
        try {
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
            val decryptedData: ByteArray
            try {
                decryptedData = cipher.doFinal(data, GCM_IV_LENGTH, dataLength)
            } finally {
                // `finally` runs on both success and failure.
                // On failure (AEADBadTagException), `decryptedData` is never assigned —
                // the exception propagates to the outer catch, which returns `data` unchanged.
                // On success, `decryptedData` is assigned before this block executes.
                // Either way, only `secret` needs registering here — `decryptedData` is
                // registered below on the success path, after it is safely assigned.
                markForWiping(secret)
            }
            // Reached only on success — register decryptedData for end-of-request wiping.
            markForWiping(decryptedData)
            return decryptedData
        } catch (e: Exception) {
            val sourceName = sourceNameFor(source)
            logger.error("Decryption failed - Source: $sourceName, DataSize: ${data.size} bytes, Error: ${e.javaClass.simpleName}")
            if (source is Encryptable<*>)
                Encryptable.setErrored(source)
            return data
        } finally {
            // Immediately zero the AES key — not deferred. `markForWiping` is end-of-request;
            // `aesKey.clear()` wipes the key bytes right here, regardless of success or failure.
            aesKey.clear()
        }
    }
}