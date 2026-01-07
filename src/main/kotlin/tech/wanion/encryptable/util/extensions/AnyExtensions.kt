package tech.wanion.encryptable.util.extensions

import tech.wanion.encryptable.EncryptableContext
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Delegates to EncryptableContext's markForWiping method.
 */
fun markForWiping(vararg objects: Any) = EncryptableContext.markForWiping(objects)

// Cache for fields to improve performance
private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, Field>()

/**
 * Retrieves the value of a declared field by name, searching through the class hierarchy if necessary.
 * The field is made accessible for reflection-based operations.
 * Results are cached for performance.
 *
 * @receiver The object instance to retrieve the field value from.
 * @param fieldName The name of the field to retrieve.
 * @return The value of the specified field cast to the expected type.
 * @throws NoSuchFieldException if the field is not found in the class hierarchy.
 */
@Suppress("UNCHECKED_CAST")
fun <R> Any.getField(fieldName: String): R {
    var clazz: Class<*> = this::class.java
    val key = Pair(clazz, fieldName)
    val field = fieldCache.computeIfAbsent(key) {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return@computeIfAbsent current.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("Field '$fieldName' not found in class hierarchy of ${this::class.java}")
    }
    return field.get(this) as R
}