package tech.wanion.encryptable.config

import org.springframework.core.env.Environment
import tech.wanion.encryptable.util.extensions.EMPTY
import tech.wanion.encryptable.util.extensions.getBean

/**
 * Configuration holder for Encryptable Framework settings.
 * Used to make certain values configurable at runtime.
 */
object EncryptableConfig {
    /**
     * Thread limit for parallel encryption/decryption operations.
     * Default is number of available processors multiplied by a configurable percentage.
     * See `thread.limit.percentage` property.
     */
    val threadLimit: Int

    /**
     * Storage threshold for storing in Storage vs inline.
     * Default is 1024 bytes.
     * See `encryptable.storage.threshold` property.
     */
    val storageThreshold: Int

    /**
     * Flag indicating whether integrity checks should be performed on Encryptable entities.
     * Default is true.
     * See `encryptable.integrity.check` property.
     */
    val integrityCheck: Boolean

    /**
     * Flag indicating whether the application is currently performing a migration process.
     * This can be used to conditionally disable certain features or checks during migration for performance reasons.
     * Default is false, and should be set to true during migration processes.
     */
    val migration: Boolean

    init {
        val environment = getBean(Environment::class.java)

        // Thread limit for parallel encryption/decryption operations
        // Default is number of available processors
        // better documentated at `tech.wanion.encryptable.util.Limited`
        val percentLimit = minOf(environment.getProperty("thread.limit.percentage", String.EMPTY).toFloatOrNull() ?: 0.38f, 1.0f)
        this.threadLimit = maxOf(1, (Runtime.getRuntime().availableProcessors() * percentLimit).toInt())

        // GridFS threshold for storing in External Storage vs regular document
        // Ensuring a minimum threshold of 16384 bytes (16KB)
        val gridFsThreshold = environment.getProperty("encryptable.storage.threshold", String.EMPTY).toIntOrNull() ?: 0
        this.storageThreshold = maxOf(16384, gridFsThreshold)

        // Should integrity checks be performed on Encryptable entities?
        // Default is true
        // Can be disabled for performance reasons, not recommended.
        this.integrityCheck = environment.getProperty("encryptable.integrity.check", "true").toBoolean()

        // Is the application currently performing a migration process?
        // This can be used to conditionally disable certain features or checks during migration for performance reasons.
        // Default is false, and should be set to true during migration processes.
        this.migration = environment.getProperty("encryptable.migration", "false").toBoolean()
    }
}