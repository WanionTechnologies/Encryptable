package tech.wanion.encryptable.util.extensions

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.FastByteArrayOutputStream
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.full.functions
import kotlin.reflect.full.instanceParameter
import tech.wanion.encryptable.EncryptableContext

/**
 * Creates a copy of a data class instance using its generated copy() method.
 *
 * @param instance The data class instance to copy.
 * @return A new instance with the same property values as the original.
 * @throws IllegalArgumentException if the instance is not a data class.
 */
inline fun <reified D: Any> copyDataInstance(instance: D): D {
    val instanceKClass = instance::class
    require(instanceKClass.isData) { "instance must be a data class" }

    val copyFunction = instanceKClass.functions.single { function -> function.name == "copy" }

    val copy = copyFunction.callBy(mapOf(copyFunction.instanceParameter!! to instance))

    return copy as? D ?: error("copy didn't return a new instance")
}

/**
 * Converts a Boolean to its integer representation (true = 1, false = 0).
 */
val Boolean.int: Int get() = if (this) 1 else 0

/**
 * Checks if the given CharSequence matches the pattern.
 *
 * @receiver The compiled Pattern.
 * @param charSequence The input to match against the pattern.
 * @return True if the input matches the pattern, false otherwise.
 */
fun Pattern.matches(charSequence: CharSequence): Boolean = this.matcher(charSequence).matches()

/**
 * Creates a ResponseEntity with a Location header from a string URL and the given HTTP status.
 *
 * @receiver The HTTP status to use.
 * @param location The location URL as a string.
 * @return A ResponseEntity with the Location header set.
 */
fun HttpStatus.location(location: String): ResponseEntity<Any> = location(URI(location))

/**
 * Creates a ResponseEntity with a Location header from a URI and the given HTTP status.
 *
 * @receiver The HTTP status to use.
 * @param location The location URI.
 * @return A ResponseEntity with the Location header set.
 */
fun HttpStatus.location(location: URI): ResponseEntity<Any> = ResponseEntity.status(this).location(location).build()

/**
 * Returns a ResponseEntity with the given HTTP status and no body.
 */
val HttpStatus.responseEntity: ResponseEntity<Any>
    get() = ResponseEntity.status(this).build()

/**
 * Converts an HttpStatusCode to an HttpStatus.
 */
val HttpStatusCode.httpStatus: HttpStatus get() = HttpStatus.valueOf(this.value())

/**
 * Returns the recommended content disposition ("inline" or "attachment") for the media type.
 */
val MediaType.disposition: String get() =
    if (this.type == "image" || this.type == "text" || this == MediaType.APPLICATION_PDF) "inline" else "attachment"

/**
 * Gets the class of the first generic parameter of the receiver's superclass, if available.
 *
 * @receiver The instance whose generic superclass parameter is inspected.
 * @return The Class of the first generic parameter, or null if not available.
 */
inline fun <reified T : Any> T.getGenericParameterClass(): Class<*>? {
    // Get the Java super type, which includes generic information
    val javaSuperType = this::class.java.genericSuperclass
    // Check if it's a ParameterizedType (a type with generic parameters)
    if (javaSuperType is ParameterizedType) {
        // Get the first generic argument
        val actualType = javaSuperType.actualTypeArguments.first()
        // If it's a Class
        if (actualType is Class<*>) {
            return actualType.javaClass
        }
        if (actualType is TypeVariable<*>) {
            val parameterizedType = actualType.bounds[0] as ParameterizedType
            val raw = parameterizedType.rawType
            return if (raw is Class<*>) raw else null
        }
    }
    return null
}

@OptIn(ExperimentalContracts::class)
/**
 * Checks if the value is null, throwing an exception if it is.
 *
 * @param value The value to check.
 * @throws IllegalArgumentException if the value is not null.
 */
fun <T : Any> requireNull(value: T?) {
    contract {
        returns() implies (value == null)
    }
    return requireNull(value) { "Required value was null." }
}

@OptIn(ExperimentalContracts::class)
/**
 * Checks if the value is null, throwing an exception with a custom message if it is.
 *
 * @param value The value to check.
 * @param lazyMessage A function that provides the exception message if the value is not null.
 * @throws IllegalArgumentException if the value is not null.
 */
inline fun <T : Any> requireNull(value: T?, lazyMessage: () -> Any) {
    contract {
        returns() implies (value == null)
    }

    if (value != null) {
        val message = lazyMessage()
        throw IllegalArgumentException(message.toString())
    }
}

/**
 * Retrieves a Spring bean by its class type.
 *
 * @param beanClass The class of the bean to retrieve.
 * @return The bean instance of the specified class.
 */
inline fun <reified T:Any> getBean(beanClass: Class<T> = T::class.java): T =
    EncryptableContext.applicationContext.getBean(beanClass)

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

private val allFieldsCache = ConcurrentHashMap<Class<*>, List<Field>>()

/**
 * Returns all declared fields for this class and its superclasses (excluding Object).
 * Results are cached for performance.
 *
 * The returned list includes private, protected, and public fields from the entire class hierarchy.
 * Fields are made accessible for reflection-based operations.
 *
 * @receiver The class to retrieve fields from.
 * @return List of all declared fields in the class hierarchy.
 */
val Class<*>.allFields: List<Field> get() =
    allFieldsCache.computeIfAbsent(this) {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = it
        while (current != null && current != Any::class.java && current != Object::class.java) {
            fields += current.declaredFields.onEach { f ->
                f.isAccessible = true
            }
            current = current.superclass
        }
        fields
    }

/**
 * Retrieves a declared method by name and parameter types, making it accessible.
 */
fun Class<*>.getAnyMethod(name: String, vararg parameterTypes: Class<*>): Method {
    val method: Method = this.getDeclaredMethod(name, *parameterTypes)
    method.isAccessible = true
    return method
}

/**
 * Reads all bytes from the InputStream into a ByteArray using a FastByteArrayOutputStream for efficiency.
 *
 * @receiver InputStream The input stream to read from.
 * @return ByteArray The byte array containing all data read from the stream.
 */
fun InputStream.readFastBytes(): ByteArray {
    val buffer = FastByteArrayOutputStream(maxOf(DEFAULT_BUFFER_SIZE, this.available()))
    copyTo(buffer)
    return buffer.toByteArrayUnsafe()
}

private const val cacheKey = "wanion.encryptable.util.extensions.ip"

/**
 * Retrieves the client's IP address from the HttpServletRequest.
 * It first checks the "X-Forwarded-For" header for proxies/load balancers,
 * falling back to the remote address if the header is absent.
 * The result is cached in the request attributes for efficiency.
 *
 * @receiver HttpServletRequest The HTTP request object.
 * @return String The client's IP address.
 */
val HttpServletRequest.ip: String get() {
    val cached = this.getAttribute(cacheKey) as? String
    if (cached != null) return cached
    val xForwardedForHeader = this.getHeader("X-Forwarded-For")
    val ip = if (!xForwardedForHeader.isNullOrEmpty()) {
        xForwardedForHeader.split(",").firstOrNull()?.trim() ?: this.remoteAddr
    } else {
        this.remoteAddr
    }
    this.setAttribute(cacheKey, ip)
    return ip
}

/**
 * Returns the type parameter at the given index of a generic field (e.g., List, Map).
 *
 * @param index The index of the type parameter to retrieve (e.g., 0 for List<T>, 0 for Map<K, V> key, 1 for Map<K, V> value).
 * @throws IllegalArgumentException if the field is not generic, the index is out of bounds, or the type parameter cannot be determined.
 *
 * Example usage:
 *   val type = myField.typeParameter(0)
 *   // type will be MyType::class.java for List<MyType>
 */
fun Field.typeParameter(index: Int = 0): Class<*> {
    val genericType = this.genericType
    if (genericType is ParameterizedType) {
        val typeArgs = genericType.actualTypeArguments
        if (index < 0 || index >= typeArgs.size) {
            throw IllegalArgumentException("Type parameter index $index out of bounds for field '${this.name}'.")
        }
        val typeArg = typeArgs[index]
        return when (typeArg) {
            is Class<*> -> typeArg
            is ParameterizedType -> typeArg.rawType as? Class<*> ?: throw IllegalArgumentException("Type parameter at index $index of field '${this.name}' is not a Class.")
            else -> throw IllegalArgumentException("Type parameter at index $index of field '${this.name}' could not be determined.")
        }
    }
    throw IllegalArgumentException("Field '${this.name}' is not generic or type parameter could not be determined.")
}

/**
 * Checks if all elements in the list are instances of the specified type.
 *
 * @param typeClass The Class object representing the type to check against.
 * @return True if all elements are of the specified type, false otherwise.
 */
fun List<*>.isListOf(typeClass: Class<*>): Boolean {
    return this.isNotEmpty() && this.all { typeClass.isInstance(it) }
}
