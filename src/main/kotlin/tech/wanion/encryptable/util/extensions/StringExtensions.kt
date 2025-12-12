package tech.wanion.encryptable.util.extensions

import org.apache.logging.log4j.util.Strings
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import tech.wanion.encryptable.util.SecurityUtils
import java.lang.reflect.Field
import java.security.SecureRandom
import java.util.*
import java.util.regex.Pattern

/**
 * Compiled regex pattern for matching and extracting components from URLs.
 *
 * The pattern captures:
 * - protocol (http or https)
 * - optional 'www' subdomain
 * - domain name
 * - top-level domain
 * - optional path
 */
private val URLPattern: Pattern = Pattern.compile("^(?<protocol>https?://)(?<subdomain>www(?=\\.))?\\.?([^/]{2,})\\.([^./]{2,})/?((?!/).+)?$")

/** SecureRandom instance for generating cryptographically secure random values. */
private val secureRandom = SecureRandom()

/**
 * Generates a cryptographically secure random secret string, URL-safe and without padding.
 *
 * Base64 is used as it can represent bytearrays in a more compact way than hexadecimal characters, but, Base64 is NOT enforced, any character can be used as a secret.
 *
 * @param byteLength The length in bytes of the random data to generate. Default is 32 bytes.
 * @return A URL-safe Base64 encoded string representing the random secret.
 * @throws IllegalArgumentException if byteLength is not positive.
 */
fun String.Companion.randomSecret(byteLength: Int = 32): String {
    require(byteLength > 0) { "byteLength must be positive (got: $byteLength)" }
    val bytes = ByteArray(byteLength)
    secureRandom.nextBytes(bytes)
    val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    return if (SecurityUtils.hasMinimumEntropy(secret)) secret else randomSecret(byteLength)
}

/**
 * Generates a random hexadecimal string of the specified length.
 *
 * @param length The length of the hex string to generate. Default is 24.
 * @return A random hexadecimal string.
 */
fun String.Companion.getRandomHexString(length: Int = 24): String {
    val random = SecureRandom()
    val hexChars = "0123456789abcdef"
    val result = StringBuilder(length)
    for (i in 0 until length) {
        result.append(hexChars[random.nextInt(hexChars.length)])
    }
    return result.toString()
}

/**
 * Provides the compiled URL pattern used for matching URLs.
 */
val String.Companion.urlPattern: Pattern
    get() = URLPattern

/**
 * Returns an empty string constant.
 */
val String.Companion.EMPTY: String
    get() = Strings.EMPTY

/**
 * Decodes a Base64-encoded string into a ByteArray.
 *
 * @receiver The Base64-encoded string.
 * @return The decoded ByteArray.
 * @throws IllegalArgumentException if the input is not valid Base64.
 */
fun String.decode64(): ByteArray = Base64.getDecoder().decode(this)

/**
 * Decodes a URL-safe Base64-encoded string into a ByteArray.
 *
 * @receiver The URL-safe Base64-encoded string.
 * @return The decoded ByteArray.
 * @throws IllegalArgumentException if the input is not valid Base64.
 */
fun String.decodeUrl64(): ByteArray = Base64.getUrlDecoder().decode(this)

/**
 * Converts a hexadecimal string to a ByteArray.
 *
 * @receiver The hex string (must have even length).
 * @return The corresponding ByteArray.
 * @throws IllegalArgumentException if the string length is not even or contains invalid hex characters.
 */
fun String.hexToByteArray(): ByteArray {
    require(length and 1 == 0) { "Hex string must have even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

/**
 * Parses the string as a hexadecimal color value and returns its integer representation.
 * Supports formats: "0x...", "#...", or plain hex.
 *
 * @receiver The color string.
 * @return The integer color value, or null if parsing fails.
 */
fun String.toIntColor(): Int? {
    try {
        if (this.startsWith("0x")) {
            return Integer.parseInt(this.substring(2), 16)
        } else if (this.startsWith("#")) {
            return Integer.parseInt(this.substring(1), 16)
        }
        return Integer.parseInt(this, 16)
    } catch (ex: Exception) {
        return null
    }
}

/**
 * Attempts to determine the media type of the string (usually a filename or extension).
 *
 * @receiver The filename or extension string.
 * @return The detected MediaType, or MediaType.APPLICATION_OCTET_STREAM if unknown.
 */
fun String.getMediaType(): MediaType = MediaTypeFactory.getMediaType(this).orElse(MediaType.APPLICATION_OCTET_STREAM)

/**
 * Returns a new string with the specified extension, replacing the first extension if present.
 *
 * @receiver The original filename string.
 * @param newExtension The new extension to use (with or without leading dot).
 * @return The filename with the new extension.
 */
fun String.withExtension(newExtension: String): String {
    val ext = if (newExtension.startsWith(".")) newExtension else ".${newExtension}"
    val firstDot = this.indexOf('.')
    return if (firstDot != -1) this.substring(0, firstDot) + ext else this + ext
}

/**
 * Cached reference to the internal String value field for performance.
 */
private val cachedStringValueField: Field by lazy {
    try {
        val field = String::class.java.getDeclaredField("value")
        field.isAccessible = true
        field
    } catch (e: Exception) {
        throw RuntimeException("Unable to access String.value via reflection. If you see InaccessibleObjectException, add JVM arg: --add-opens java.base/java.lang=ALL-UNNAMED", e)
    }
}

/**
 * Cached reference to the internal String hash field for performance.
 */
private val cachedStringHashField: Field by lazy {
    try {
        // Try common field names for hash code
        val field = try {
            String::class.java.getDeclaredField("hash")
        } catch (_: Exception) {
            String::class.java.getDeclaredField("hashCode")
        }
        field.isAccessible = true
        field
    } catch (e: Exception) {
        throw RuntimeException("Unable to access String.hash or String.hashCode via reflection. If you see InaccessibleObjectException, add JVM arg: --add-opens java.base/java.lang=ALL-UNNAMED", e)
    }
}

/**
 * Cached reference to the internal String hashIsZero field for performance (if present).
 */
private val cachedStringHashIsZeroField: Field? by lazy {
    try {
        val field = String::class.java.getDeclaredField("hashIsZero")
        field.isAccessible = true
        field
    } catch (_: Exception) {
        null // Field not present in this JVM
    }
}

/**
 * Attempts to overwrite the internal value array (char[] or byte[]) of this String with zeros.
 * Also resets the cached hash code field. Uses cached reflection for performance.
 * Compatible with Java 21 compact strings.
 * Throws RuntimeException if clearing fails, to avoid silent privacy failures.
 * If the internal hashIsZero field exists, sets it to true for consistency with JVM caching.
 */
fun String.zerify() {
    // if String is already all zeros, skip clearing
    if (this.hashCode() == 0)
        return
    try {
        // Overwrite internal value array
        when (val value = cachedStringValueField.get(this)) {
            is CharArray -> value.fill('\u0000')
            is ByteArray -> value.fill(0)
            else -> throw RuntimeException("String.value is not a CharArray or ByteArray. Clearing failed.")
        }
        // Reset cached hash code
        cachedStringHashField.setInt(this, 0)
        // Set hashIsZero to true if present
        cachedStringHashIsZeroField?.setBoolean(this, true)
    } catch (e: Exception) {
        throw RuntimeException("Unable to clear String.value or String.hash/hashCode. If you see InaccessibleObjectException, add JVM arg: --add-opens java.base/java.lang=ALL-UNNAMED", e)
    }
}