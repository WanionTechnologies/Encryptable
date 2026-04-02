package tech.wanion.encryptable

import org.bson.types.Binary
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.util.SecurityUtils
import tech.wanion.encryptable.util.extensions.decodeUrl64
import tech.wanion.encryptable.util.extensions.encodeURL64
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*

/**
 * CID (Compact ID) is a compact, URL-safe, cryptographically derived identifier.
 *
 * - 16 bytes (128 bits) of entropy, same as a UUID.
 * - Encoded as a 22-character URL-safe Base64 string (no padding).
 * - Suitable for use as a primary key, entity identifier, or anywhere a secure, unique, and URL-friendly ID is needed.
 *
 * Example usage:
 * ```kotlin
 * // assuming `myBytes` is a ByteArray of length 16.
 * val cid = CID.fromBytes(myBytes)
 * val cidString = cid.toString() // URL-safe Base64 string
 * val parsed = cidString.cid // Parse from string
 * val randomCid = CID.random() // Generate a random CID
 * val uuid = cid.uuid // Convert to UUID
 * val fromUuid = uuid.cid // Convert from UUID
 * ```
 *
 * @property bytes The 16-byte array representing the ID.
 */
class CID(bytes: ByteArray) {
    /**
     * The underlying byte array representing the CID.
     */
    val bytes: ByteArray

    /**
     * Ensures the byte array is exactly 16 bytes.
     * @throws IllegalArgumentException if the byte array is not 16 bytes.
     */
    init {
        require(bytes.size == 16) { "a CID must be exactly 16 bytes (128 bits)" }
        this.bytes = bytes.copyOf()
    }

    companion object {
        /**
         *  Secure random generator for CID generation.
         */
        private val random = SecureRandom()

        /**
         * Generates a new random CID.
         */
        fun random(): CID = randomCIDString().cid

        /**
         * Generates a new random CID String.
         */
        fun randomCIDString(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            val cidString = bytes.encodeURL64()
            // Defensive: 16 bytes encoded as URL-safe Base64 (no padding) should always yield 22 chars.
            // If not, something went wrong... retry to guarantee correct CID length.
            // Also ensure it meets minimum entropy requirements.
            return if (cidString.length == 22 && SecurityUtils.hasMinimumEntropy(cidString)) cidString else randomCIDString()
        }

        /**
         * Creates an CID from a 16-byte array.
         * @throws IllegalArgumentException if the array is not 16 bytes.
         */
        fun fromBytes(bytes: ByteArray): CID = CID(bytes)

        /**
         * Creates an CID from a URL-safe Base64 string (22 characters, no padding).
         * @throws IllegalArgumentException if the decoded array is not 16 bytes.
         */
        fun fromBase64Url(base64: String): CID = base64.decodeUrl64().cid

        /**
         * Creates an CID from standard Base64 with padding (the format displayed by MongoDB Compass).
         * This is a convenience function for converting MongoDB Compass-displayed CIDs back to native format.
         * @throws IllegalArgumentException if the decoded array is not 16 bytes.
         */
        fun fromBase64Padded(base64: String): CID = Base64.getDecoder().decode(base64).cid

        /**
         * Extension function to convert a Base64 or hex string to a CID.
         *
         * **Accepts multiple formats:**
         * - **22 characters**: URL-safe Base64 without padding (native CID format) — `"ABC123_-xyz..."`
         * - **24 characters**: Standard Base64 with padding (MongoDB Compass format) — `"ABC123/+xyz=="`
         * - **32 characters**: Hexadecimal UUID without hyphens — `"550e8400e29b41d4a716446655440000"`
         * - **36 characters**: Standard UUID format with hyphens — `"550e8400-e29b-41d4-a716-446655440000"`
         *
         * This flexibility allows seamless round-tripping with [CID.toString] output and conversion from UUID format:
         * - When `true` (default): `toString()` produces standard Base64 (24 chars) → `"ABC123/+xyz==".cid` works
         * - When `false`: `toString()` produces URL-safe Base64 (22 chars) → `"ABC123_-xyz...".cid` works
         * - Always works: UUID formats → `"550e8400e29b41d4a716446655440000".cid` or `"550e8400-e29b-41d4-a716-446655440000".cid`
         *
         * @throws IllegalArgumentException if the input is not a valid CID string:
         *   - Not exactly 22, 24, 32, or 36 characters
         *   - Invalid Base64 or UUID/hex encoding
         *   - Does not decode to exactly 16 bytes
         *
         * @see [CID.toString] for format controlled by `encryptable.cid.base64` config
         * @see [toBase64Padded] to explicitly get standard Base64 format
         */
        val String.cid: CID get() = when(this.length) {
            22 -> fromBase64Url(this)
            24 -> fromBase64Padded(this)
            32, 36 -> {
                // Handle both 32-char (no hyphens) and 36-char (with hyphens) UUID hex formats
                val normalized = if (this.length == 36) this else
                    this.replaceRange(8, 8, "-").replaceRange(13, 13, "-").replaceRange(18, 18, "-").replaceRange(23, 23, "-")
                UUID.fromString(normalized).cid
            }
            else -> throw IllegalArgumentException("Invalid CID string length: expected 22 (URL-safe Base64), 24 (standard Base64 with padding), 32 (UUID hex), or 36 (standard UUID), got ${this.length}")
        }

        /**
         * Extension function to convert a 16-byte array to an EID.
         * Usage: val eid = myByteArray.eid
         *
         * @throws IllegalArgumentException if the array is not 16 bytes.
         */
        val ByteArray.cid: CID
            get() = fromBytes(this)

        /**
         * Converts this CID to a UUID.
         */
        val CID.uuid: UUID
            get() {
                val bb = ByteBuffer.wrap(this.bytes)
                val high = bb.long
                val low = bb.long
                return UUID(high, low)
            }

        /**
         * Converts a UUID to a CID.
         */
        val UUID.cid: CID
            get() {
                val bb = ByteBuffer.allocate(16)
                bb.putLong(this.mostSignificantBits)
                bb.putLong(this.leastSignificantBits)
                return CID(bb.array())
            }

        /**
         * Custom BSON Binary subtype 128 (user-defined) for CID format.
         *  We use a custom subtype rather than standard 0x04 (UUID) because CID is not a standard UUID,
         *  but a custom cryptographic identifier. MongoDB reserves subtypes 128+ for user-defined types.
         *  This allows tools and systems to distinguish CID data from standard UUID data.
         */
        private const val CID_BSON_SUBTYPE = 128.toByte()

        /**
         * Converts this CID to a BSON Binary with custom subtype 128 (user-defined).
         */
        val CID.binary: Binary get() = Binary(CID_BSON_SUBTYPE, bytes)

        /**
         * Converts a BSON Binary with custom subtype 128 to a CID.
         * @throws IllegalArgumentException if the Binary subtype is not 128.
         */
        val Binary.cid: CID get() {
            require(this.type == CID_BSON_SUBTYPE) { "Expected BSON Binary subtype 128 (custom/user-defined) for CID" }
            return fromBytes(this.data)
        }
    }

    /**
     * Returns the string representation of this CID.
     *
     * **Format behavior (changed in version 1.1.0):**
     * - Before 1.1.0: Always returned URL-safe Base64 (22 characters)
     * - From 1.1.0 onwards: Format depends on the `encryptable.cid.base64` configuration:
     *   - `true` (default): standard Base64 with padding — matches MongoDB Compass display for BSON Binary custom subtype 128
     *   - `false`: URL-safe Base64 without padding (22 characters) — the previous default behavior
     *
     * Use [toBase64Url] to explicitly get the URL-safe format, or [toBase64Padded] for the standard format.
     */
    override fun toString(): String =
        if (EncryptableConfig.cidBase64) toBase64Padded() else toBase64Url()

    /**
     * Returns the URL-safe Base64 representation of this CID without padding (22 characters).
     * This was the default format until the `encryptable.cid.base64` configuration was introduced in version 1.1.0.
     * This is always the native CID format, regardless of the current configuration.
     *
     * Use this when you need the compact format for URLs, QR codes, or external APIs.
     *
     * @return URL-safe Base64-encoded string (no padding)
     */
    fun toBase64Url(): String = bytes.encodeURL64()

    /**
     * Returns the standard Base64 representation of this CID with padding (24 characters).
     * This is the format displayed by MongoDB Compass for BSON Binary custom subtype 128 fields.
     *
     * Use this when debugging or interoperating with systems that expect standard Base64 format.
     *
     * @return Standard Base64-encoded string (with padding)
     */
    fun toBase64Padded(): String = Base64.getEncoder().encodeToString(bytes)

    /**
     * Checks equality based on the underlying byte array content.
     */
    override fun equals(other: Any?): Boolean = other is CID && bytes.contentEquals(other.bytes)

    /**
     * Hash code based on the byte array content.
     */
    override fun hashCode(): Int = bytes.contentHashCode()
}