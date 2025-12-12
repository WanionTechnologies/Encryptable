package tech.wanion.encryptable.mongo

import org.bson.types.Binary
import tech.wanion.encryptable.util.SecurityUtils
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import tech.wanion.encryptable.util.extensions.decodeUrl64
import tech.wanion.encryptable.util.extensions.encodeURL64

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
        fun fromBase64(base64: String): CID = base64.decodeUrl64().cid

        /**
         * Extension function to convert a URL-safe Base64 string to an CID.
         * Usage: val cid = myString.cid
         *
         * The input string must be a 22-character URL-safe Base64 encoding (no padding) representing exactly 16 bytes.
         * If the string is not valid Base64, or not exactly 22 characters, or does not decode to 16 bytes,
         * this property will throw an IllegalArgumentException.
         *
         * @throws IllegalArgumentException if the decoded array is not 16 bytes or the input is not a valid 22-character Base64 string.
         */
        val String.cid: CID
            get() = fromBase64(this)

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
         * Converts a UUID to an CID.
         */
        val UUID.cid: CID
            get() {
                val bb = ByteBuffer.allocate(16)
                bb.putLong(this.mostSignificantBits)
                bb.putLong(this.leastSignificantBits)
                return CID(bb.array())
            }

        /** Byte value for BSON Binary subtype 0x04. */
        private const val FOUR_AS_BYTE = 4.toByte()

        /**
         * Converts this CID to a BSON Binary with subtype 0x04.
         */
        val CID.binary: Binary get() = Binary(FOUR_AS_BYTE, bytes)

        /**
         * Converts a BSON Binary with subtype 0x04 to a CID.
         * @throws IllegalArgumentException if the Binary subtype is not 0x04.
         */
        val Binary.cid: CID get() {
            require(this.type == FOUR_AS_BYTE) { "Expected BSON Binary subtype 0x04 for CID" }
            return fromBytes(this.data)
        }
    }

    /**
     * Returns the URL-safe Base64 string representation of this CID (22 characters, no padding).
     */
    override fun toString(): String = bytes.encodeURL64()

    /**
     * Checks equality based on the underlying byte array content.
     */
    override fun equals(other: Any?): Boolean = other is CID && bytes.contentEquals(other.bytes)

    /**
     * Hash code based on the byte array content.
     */
    override fun hashCode(): Int = bytes.contentHashCode()

}