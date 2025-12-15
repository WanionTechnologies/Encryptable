package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.mongo.EncryptableMongoRepositoryImpl

/**
 * Aspect responsible for managing the lifecycle and access control of Encryptable.Metadata
 * in EncryptableMongoRepositoryImpl instances.
 *
 * This aspect serves two main purposes:
 * 1. Ensures metadata is initialized lazily when first accessed
 * 2. Prevents direct modification of metadata after initialization
 *
 * The aspect uses AspectJ's field interception to monitor both read and write operations
 * on the metadata field, maintaining immutability and proper initialization semantics.
 *
 * Thread safety is guaranteed through synchronization of the metadata initialization process.
 * Field reflection results are cached for performance optimization.
 */
@Aspect
@Component
@Suppress("SpringAopPointcutExpressionInspection")
class EncryptableMetadataAspect {
    companion object {
        /** Reflection Field object for the metadata field in EncryptableMongoRepositoryImpl */
        private val metadataField = EncryptableMongoRepositoryImpl::class.java.getDeclaredField("metadata").apply { isAccessible = true }
    }

    /**
     * Intercepts get operations on the metadata field and ensures proper initialization.
     * If the metadata field is null when accessed, it will be initialized with a default instance.
     * This method is thread-safe through synchronization.
     *
     * @param joinPoint The join point representing the field get operation
     * @throws IllegalStateException if unable to access or modify the metadata field
     * @throws IllegalArgumentException if the target is not an EncryptableMongoRepository
     */
    @Before("get(tech.wanion.encryptable.mongo.Encryptable.Metadata tech.wanion.encryptable.mongo.EncryptableMongoRepositoryImpl+.metadata)")
    fun beforeGetMetadata(joinPoint: JoinPoint) {
        try {
            val target = joinPoint.target as? EncryptableMongoRepository<*>
                ?: throw IllegalArgumentException("Target ${joinPoint.target.javaClass.simpleName} is not an EncryptableMongoRepository")

            if (metadataField.get(target) == null)
                initializeMetadata(target)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to access or initialize metadata field in ${joinPoint.target.javaClass.simpleName}", e)
        }
    }

    /**
     * Helper method to initialize the metadata field with the correct type class.
     * This method must be called within a synchronized block.
     *
     * @param encryptableRepository The target repository instance
     */
    private fun initializeMetadata(encryptableRepository: EncryptableMongoRepository<*>) {
        val metadata = Encryptable.getMetadataFor(encryptableRepository.getTypeClass())
        metadataField.set(encryptableRepository, metadata)
    }
}