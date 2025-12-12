package tech.wanion.encryptable.util

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Utility object for hashing strings using the SHA-512 algorithm.
 * Provides a method to generate a hexadecimal hash from a string input.
 */
object SHA512 {
    /**
     * Hashes a string using SHA-512 and returns the result as a lowercase hexadecimal string.
     * Uses UTF-8 encoding for string conversion.
     * @param toHash The string to hash.
     * @return The SHA-512 hash as a lowercase hexadecimal string.
     */
    @JvmStatic
    fun hash(toHash: String): String = hash(toHash.toByteArray(StandardCharsets.UTF_8))

    /**
     * Hashes a byte array using SHA-512 and returns the result as a lowercase hexadecimal string.
     * @param bytes The byte array to hash.
     * @return The SHA-512 hash as a lowercase hexadecimal string.
     */
    @JvmStatic
    fun hash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Hashes the contents of an InputStream using SHA-512 and returns the result as a lowercase hexadecimal string.
     * The stream will be read in 4MB chunks and will be closed after hashing.
     * @param input The InputStream to hash.
     * @return The SHA-512 hash as a lowercase hexadecimal string.
     */
    @JvmStatic
    fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(4 * 1024 * 1024) // 4MB buffer
        var read: Int
        try {
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        } finally {
            input.close()
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }
}