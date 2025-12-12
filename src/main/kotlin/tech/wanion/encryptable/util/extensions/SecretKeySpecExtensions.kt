package tech.wanion.encryptable.util.extensions

import java.lang.reflect.Field
import javax.crypto.spec.SecretKeySpec

/**
 * Cached reflection field to access the internal key byte array of SecretKeySpec for performance.
 */
private val cachedKeyField: Field by lazy {
    try {
        val field = SecretKeySpec::class.java.getDeclaredField("key")
        field.isAccessible = true
        field
    } catch (e: Exception) {
        throw RuntimeException("Unable to access SecretKeySpec.key via reflection. If you see InaccessibleObjectException, add JVM arg: --add-opens java.base/javax.crypto.spec=ALL-UNNAMED", e)
    }
}

/**
 * Extension function to securely clear the key material in SecretKeySpec.
 * Overwrites the internal key byte array with zeros using cached reflection.
 * Throws RuntimeException if clearing fails, to avoid silent privacy failures.
 */
fun SecretKeySpec.clear() {
    val keyBytes = cachedKeyField.get(this) as? ByteArray
    keyBytes?.fill(0) ?: throw RuntimeException("SecretKeySpec.key is not a ByteArray. Clearing failed.")
}