package tech.wanion.encryptable.util.extensions

import tech.wanion.encryptable.EncryptableContext
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Delegates to EncryptableContext's markForWiping method.
 */
fun markForWiping(vararg objects: Any) = EncryptableContext.markForWiping(objects)

/** Cache to improve performance of `Any.getField(fieldName: String)`field access. */
private val fieldCache = ConcurrentHashMap<Pair<Class<*>, String>, Field>()

/**
 * Retrieves the value of a field with the specified name from the object, searching through the class hierarchy if necessary.
 * The field is made accessible if it is not already, allowing access to private and protected fields.
 *
 * @receiver The object from which to retrieve the field value.
 * @param fieldName The name of the field to retrieve.
 * @return The value of the specified field, cast to the expected type [R].
 * @throws NoSuchFieldException If the field with the specified name is not found in the class hierarchy.
 */
@Suppress("UNCHECKED_CAST")
fun <R> Any.readField(fieldName: String): R {
    val clazz: Class<*> = this::class.java
    val key = Pair(clazz, fieldName)
    val field = fieldCache.computeIfAbsent(key) {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return@computeIfAbsent current.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("Field '$fieldName' not found in class hierarchy of ${this::class.java}")
    }
    return field.get(this) as R
}

/**
 * Retrieves the metadata string for a Field, which includes the declaring class name and the field name.
 * The format of the metadata string is: "DeclaringClassName/FieldName".
 *
 * @receiver The Field instance to retrieve metadata from.
 * @return A string representing the metadata of the field.
 */
val Field.metadata: String get() = "${this.declaringClass.name}/${this.name}"