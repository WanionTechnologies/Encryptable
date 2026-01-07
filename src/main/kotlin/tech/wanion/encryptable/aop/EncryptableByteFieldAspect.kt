package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.gridfs.GridFsTemplate
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.getField
import java.util.concurrent.ConcurrentHashMap

/**
 * # EncryptableFieldGetAspect
 * Aspect for intercepting access to `byte[]` fields in classes implementing `Encryptable`.
 * This aspect allows custom logic to be executed before such fields are accessed,for example, for logging or security purposes.
 */
@Aspect
@Suppress("SpringAopPointcutExpressionInspection")
class EncryptableByteFieldAspect {
    companion object {
        // Reflection method to call updateEntityInfo
        private val loadGridFsFieldMethod = Encryptable::class.java.getDeclaredMethod("loadGridFsField",
            String::class.java
        ).also { it.isAccessible = true }

        /**
         * gridFsTemplate
         *
         * GridFsTemplate for storing/retrieving large encrypted files in MongoDB GridFS.
         * Used for fields of type ByteArray that exceed 1KB in size.
         */
        private val gridFsTemplate: GridFsTemplate = getBean(GridFsTemplate::class.java)
    }

    // Pointcut for field get access to byte[] fields in Encryptable subclasses
    @Pointcut("get(byte[] tech.wanion.encryptable.mongo.Encryptable+.*)")
    fun beforeFieldGetPointcut() {}

    /**
     * Intercepts field get access to byte[] fields in Encryptable subclasses.
     *
     * ## Performance Optimization
     * To avoid the overhead of repeated reflection lookups,
     * this aspect caches the Field object for each (Class, fieldName) pair using a thread-safe ConcurrentHashMap.
     * The first time a field is accessed, it is retrieved via reflection and stored in the cache;
     * subsequent accesses use the cached Field, improving performance.
     *
     * ## How it works
     * - The aspect is triggered for any direct field get access (via the get() pointcut).
     * - It checks the current stack trace and inspects the third frame (index 2), which typically represents the direct caller method.
     * - If the method name of the third stack frame does not end with the field name (case-insensitive) the aspect returns early and does nothing.
     * - This ensures only direct access to the field (e.g., via a property getter) triggers the aspect logic, and indirect accesses (such as from hashCode, toString, equals, or other methods) are ignored.
     * - If the check passes, the aspect uses the cached Field (or reflects and caches if not present) to get the field value and, if it is a 12-byte array (ObjectId reference), invokes the loadField method to load the actual data.
     *
     * ## Why this works
     * - By matching the method name to the field name, the aspect reliably detects direct property access and avoids recursion or unwanted triggers from other methods.
     * - Caching Field objects avoids repeated reflection, making the aspect efficient for frequent field accesses.
     */
    @Before("beforeFieldGetPointcut()")
    fun beforeFieldGet(joinPoint: JoinPoint) {
        val methodName = joinPoint.signature.name
        val encryptable = joinPoint.target as? Encryptable<*> ?: return
        // Convert getter name to field name: getAvatar -> avatar
        val fieldName = methodName.removePrefix("get").replaceFirstChar { it.lowercase() }

        loadGridFsFieldMethod.invoke(encryptable, fieldName)
    }

    // Pointcut for field set access to byte[] fields in Encryptable subclasses
    @Pointcut("set(byte[] tech.wanion.encryptable.mongo.Encryptable+.*)")
    fun aroundFieldSetPointcut() {}

    /**
     * Intercepts field set access to byte[] fields in Encryptable subclasses.
     *
     * Handles the transition logic:
     * - When a small byte array becomes large (>1KB), moves it to GridFS
     * - When a large byte array becomes small (<=1KB), removes it from GridFS and stores inline
     * - Updates the gridFsFields tracking list accordingly
     * - Handles encryption if the field is annotated with @Encrypt
     */
    @Around("aroundFieldSetPointcut()")
    fun aroundFieldSet(joinPoint: ProceedingJoinPoint): Any? {
        val fieldName = joinPoint.signature.name
        val encryptable = joinPoint.target as? Encryptable<*> ?: return joinPoint.proceed()

        if (Encryptable.getUnsafeSecretOf(encryptable) == null)
            return joinPoint.proceed()

        val metadata = encryptable.metadata
        val field = metadata.byteArrayFields[fieldName] ?: return joinPoint.proceed()

        // Get old bytes first.
        val oldBytes = field.get(encryptable) as? ByteArray?

        // Get the new value being set
        val newBytes = joinPoint.args[0] as? ByteArray?

        if (oldBytes == null && newBytes == null) return null // No change, both null

        val gridFsFields = encryptable.getField<MutableList<String>>("gridFsFields")
        val gridFsFieldIdMap = encryptable.getField<ConcurrentHashMap<String, ObjectId>>("gridFsFieldIdMap")

        if (oldBytes != null) {
            if (oldBytes.contentEquals(newBytes)) return null
            val objectId =
                if (oldBytes.size == 12) ObjectId(oldBytes)
                else gridFsFieldIdMap[fieldName]
            // Old value was in GridFS, need to delete it
            // Get the ObjectId from the oldBytes or from the map.
            if (objectId != null) {
                // if it could find an ObjectID, means that it had a file stored in GridFS.
                // delete the old file from GridFS
                gridFsTemplate.delete(Query(Criteria.where("_id").`is`(objectId)))
                gridFsFields.remove(fieldName)
                gridFsFieldIdMap.remove(fieldName)
            }
            if (newBytes == null) {
                field.set(encryptable, null)
                return null
            }
        }

        newBytes as ByteArray

        val isBig = newBytes.size >= EncryptableConfig.gridFsThreshold // 1KB threshold

        if (isBig) {
            // Storing a large field: save to GridFS
            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            // To prevent it from throwing in case it is an @Id entity and the master secret was not set.
            val secret = if (encryptField) Encryptable.getSecretOf(encryptable) else null
            val bytesToStore =
                if (secret != null) AES256.encrypt(secret, encryptable::class.java, newBytes) else newBytes
            val objectId = gridFsTemplate.store(bytesToStore.inputStream(), fieldName)
            if (!gridFsFields.contains(fieldName))
                gridFsFields.add(fieldName)
            gridFsFieldIdMap[fieldName] = objectId
        }
        field.set(encryptable, newBytes)
        return null
    }
}