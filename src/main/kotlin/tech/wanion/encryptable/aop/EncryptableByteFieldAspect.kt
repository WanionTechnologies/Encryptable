package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.unreflect
import java.util.*

/**
 * # EncryptableFieldGetAspect
 * Aspect for intercepting access to `byte[]` fields in classes implementing `Encryptable`.
 * This aspect allows custom logic to be executed before such fields are accessed,for example, for logging or security purposes.
 */
@Aspect
@Suppress("SpringAopPointcutExpressionInspection")
class EncryptableByteFieldAspect {
    companion object {
        private val setMethod = StorageHandler::class.java.getDeclaredMethod("set",
            Encryptable::class.java, String::class.java, ByteArray::class.java
        ).unreflect()

        private val throwIfReadOnlyMethod = Encryptable::class.java.getDeclaredMethod("throwIfReadOnly").unreflect()

        private val storageFieldsField = Encryptable::class.java.getDeclaredField("storageFields").also { it.isAccessible = true }
    }

    /** StorageHandler instance for handling storage operations related to byte[] fields. */
    val storageHandler: StorageHandler = getBean(StorageHandler::class.java)

    // Pointcut for field get access to byte[] fields in Encryptable subclasses
    @Pointcut("get(byte[] tech.wanion.encryptable.mongo.Encryptable+.*)")
    fun beforeFieldGetPointcut() {}

    /**
     * Intercepts field get access to byte[] fields in Encryptable subclasses.
     *
     * This method is executed before the actual field get operation. It retrieves the field name from the getter method name,
     * checks if the target object is an instance of Encryptable, and then calls the storage handler to perform any necessary operations
     * related to retrieving the field value, such as fetching it from GridFS if it is stored there.
     *
     * @param joinPoint The join point representing the intercepted method execution, providing access to method details and target object.
     */
    @Before("beforeFieldGetPointcut()")
    fun beforeFieldGet(joinPoint: JoinPoint) {
        val methodName = joinPoint.signature.name
        val encryptable = joinPoint.target as? Encryptable<*> ?: return
        // Convert getter name to field name: getAvatar -> avatar
        val fieldName = methodName.removePrefix("get").replaceFirstChar { it.lowercase() }

        // Ensure the storageFields list is initialized before accessing it in the storage handler
        ensureStorageInitialized(encryptable)

        storageHandler.get(encryptable, fieldName)
    }

    // Pointcut for field set access to byte[] fields in Encryptable subclasses
    @Pointcut("set(byte[] tech.wanion.encryptable.mongo.Encryptable+.*)")
    fun aroundFieldSetPointcut() {}

    /**
     * Intercepts field set access to byte[] fields in Encryptable subclasses.
     *
     * This method is executed around the actual field set operation. It retrieves the field name from the setter method name,
     * checks if the target object is an instance of Encryptable, and then calls the storage handler to perform any necessary operations
     * related to setting the field value, such as storing it in GridFS if it exceeds the inline storage threshold.
     *
     * @param joinPoint The proceeding join point representing the intercepted method execution, providing access to method details and target object.
     * @return The result of the original method execution if the storage handler allows it, or null if the storage handler handles the set operation itself.
     */
    @Around("aroundFieldSetPointcut()")
    fun aroundFieldSet(joinPoint: ProceedingJoinPoint): Any? {
        val encryptable = joinPoint.target as? Encryptable<*> ?: return joinPoint.proceed()

        if (Encryptable.getUnsafeSecretOf(encryptable) == null)
            return joinPoint.proceed()

        val fieldName = joinPoint.signature.name

        throwIfReadOnlyMethod.invoke(encryptable)
        // encryptable.throwIfReadOnly()

        // Ensure the storageFields list is initialized before accessing it in the storage handler
        ensureStorageInitialized(encryptable)

        // Get the new value being set
        val newBytes = joinPoint.args[0] as? ByteArray?

        setMethod.invoke(storageHandler, encryptable, fieldName, newBytes)

        return null
    }

    /** Ensures that the storageFields list in the Encryptable instance is initialized. If it is null, it initializes it with a synchronized list. */
    private fun ensureStorageInitialized(encryptable: Encryptable<*>) {
        val storageFields = storageFieldsField.get(encryptable)
        if (storageFields == null)
            storageFieldsField.set(encryptable, Collections.synchronizedList(mutableListOf<String>()))
    }
}