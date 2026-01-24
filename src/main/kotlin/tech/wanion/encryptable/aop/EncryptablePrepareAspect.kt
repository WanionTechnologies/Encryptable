package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import org.bson.types.ObjectId
import org.springframework.data.mongodb.gridfs.GridFsTemplate
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.getField
import java.util.concurrent.ConcurrentHashMap

/**
 * # EncryptablePrepareAspect
 * Aspect for intercepting Encryptable.prepare() to process byte array fields before entity preparation.
 */
@Aspect
class EncryptablePrepareAspect {
    companion object {
        /**
         * gridFsTemplate
         *
         * GridFsTemplate for storing/retrieving large encrypted files in MongoDB GridFS.
         * Used for fields of type ByteArray that exceed 1KB in size.
         */
        val gridFsTemplate: GridFsTemplate = getBean(GridFsTemplate::class.java)
    }

    // Pointcut for execution of Encryptable.prepare()
    @Pointcut("execution(* tech.wanion.encryptable.mongo.Encryptable+.prepare(..))")
    fun beforePreparePointcut() {}

    /**
     * Runs before Encryptable.prepare() and processes byte array fields that need GridFS storage.
     * This ensures that large byte arrays are stored in GridFS before the entity is prepared and persisted.
     */
    @Before("beforePreparePointcut()")
    fun beforePrepare(joinPoint: JoinPoint) {
        val encryptable = joinPoint.target as? Encryptable<*> ?: return
        val metadata = encryptable.metadata

        // Process byte array fields that need GridFS storage
        val gridFsFields = encryptable.getField<MutableList<String>>("gridFsFields")
        val gridFsFieldIdMap = encryptable.getField<ConcurrentHashMap<String, ObjectId>>("gridFsFieldIdMap")

        metadata.byteArrayFields.entries.parallelForEach { (fieldName, field) ->
            // Get current byte array value, skip if null
            val bytes = field.get(encryptable) as? ByteArray? ?: return@parallelForEach
            // If field is already processed, skip
            if (gridFsFields.contains(fieldName)) return@parallelForEach

            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            // If size < 1KB, do not use GridFS
            if (bytes.size < EncryptableConfig.gridFsThreshold)
                return@parallelForEach
            val secret = Encryptable.getSecretOf(encryptable)
            val bytesToStore = if (encryptField) AES256.encrypt(secret, encryptable::class.java, bytes) else bytes
            val inputStream = bytesToStore.inputStream()
            val objectId = gridFsTemplate.store(inputStream, fieldName)
            gridFsFieldIdMap[fieldName] = objectId
            gridFsFields.add(fieldName)
            field.set(encryptable, bytes)
        }
    }
}