package tech.wanion.encryptable.util.extensions

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.*
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.config.EncryptableConfig
import java.lang.reflect.Field

/**
 * Converts this ByteArray to a hexadecimal string representation.
 *
 * @receiver ByteArray to convert.
 * @return Hexadecimal string representation of the ByteArray.
 */
fun ByteArray.toHexString(): String = HexFormat.of().formatHex(this)

/**
 * Encodes this ByteArray into a standard Base64 string.
 *
 * @receiver ByteArray to encode.
 * @return Base64-encoded string.
 */
fun ByteArray.encode64(): String = Base64.getEncoder().encodeToString(this)

/**
 * Encodes this ByteArray into a URL-safe Base64 string without padding.
 *
 * @receiver ByteArray to encode.
 * @return URL-safe Base64-encoded string without padding.
 */
fun ByteArray.encodeURL64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

/**
 * Computes a checksum of the first 4KB of the byte array using the specified hashing algorithm.
 * Defaults to SHA-256 if no algorithm is provided.
 *
 * @param algorithm The name of the hashing algorithm to use (e.g., "SHA-256", "MD5").
 * @receiver ByteArray to compute checksum for.
 * @return An integer checksum derived from the hash of the first 4KB.
 */
fun ByteArray.first4KBChecksum(algorithm: String = "SHA-256"): Int {
    val length = minOf(this.size, 4096)
    val digest = MessageDigest.getInstance(algorithm)
    digest.update(this, 0, length)
    return digest.digest().fold(0) { acc, byte -> 31 * acc + byte }
}

/**
 * Converts the first four bytes of the ByteArray to an Int using little-endian byte order.
 *
 * @receiver ByteArray The byte array to convert.
 * @return Int The resulting integer.
 * @throws IllegalArgumentException if the byte array has fewer than 4 bytes.
 */
fun ByteArray.toIntLittleIndian(): Int {
    require(this.size >= 4) { "Byte array must contain at least 4 bytes." }
    return (this[3].toInt() shl 24) or
            ((this[2].toInt() and 0xFF) shl 16) or
            ((this[1].toInt() and 0xFF) shl 8) or
            (this[0].toInt() and 0xFF)
}

/**
 * Threshold size (in bytes) to decide when to use parallel fill.
 * Arrays smaller than this will use standard fill for lower overhead.
 */
private const val PARALLEL_FILL_THRESHOLD_BYTES = 10 * 1024 * 1024 // 10MB

/**
 * Efficiently fills this ByteArray in parallel with the specified value.
 * Uses Limited.parallelForEach on chunked ranges for optimal parallelism and resource usage.
 * For small arrays, standard fill is recommended due to lower overhead.
 *
 * @receiver ByteArray to fill.
 * @param value The byte value to set for each element.
 */
fun ByteArray.parallelFill(value: Byte) {
    if (this.isEmpty()) return
    // Use plain fill for arrays smaller than the threshold
    if (this.size < PARALLEL_FILL_THRESHOLD_BYTES) {
        this.fill(value)
        return
    }
    val processors = EncryptableConfig.threadLimit
    val chunkSize = (this.size + processors - 1) / processors
    val chunks = (0 until processors).mapNotNull { i ->
        val start = i * chunkSize
        val end = minOf(start + chunkSize, this.size)
        if (start < end) Pair(start, end) else null
    }
    chunks.parallelForEach { (start, end) ->
        for (j in start until end) {
            this[j] = value
        }
    }
}

/**
 * Reflection field to access the internal buffer of ByteArrayOutputStream.
 */
private val byteArrayOutputStreamBufField: Field by lazy {
    ByteArrayOutputStream::class.java.getDeclaredField("buf").apply { isAccessible = true }
}

/**
 * Returns the internal buffer of ByteArrayOutputStream without copying.
 * Use with caution: modifying this array affects the stream's contents.
 */
fun ByteArrayOutputStream.toByteArrayUnsafe(): ByteArray= byteArrayOutputStreamBufField.get(this) as ByteArray
