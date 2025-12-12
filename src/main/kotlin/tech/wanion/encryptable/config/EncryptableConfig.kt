package tech.wanion.encryptable.config

import org.springframework.core.env.Environment
import tech.wanion.encryptable.util.extensions.EMPTY
import tech.wanion.encryptable.util.extensions.getBean

/**
 * Configuration holder for Encryptable Framework settings.
 * Used to make certain values configurable at runtime.
 */
object EncryptableConfig {
    // Maximum number of threads for parallel operations
    val threadLimit: Int
    // GridFS storage threshold in bytes
    val gridFsThreshold: Int
    // Flag to enable or disable integrity checks
    val integrityCheck: Boolean

    init {
        val environment = getBean(Environment::class.java)

        // Thread limit for parallel encryption/decryption operations
        // Default is number of available processors
        // better documentated at `tech.wanion.encryptable.util.Limited`
        val percentLimit = minOf(environment.getProperty("thread.limit.percentage", String.EMPTY).toFloatOrNull() ?: 0.34f, 1.0f)
        this.threadLimit = maxOf(1, (Runtime.getRuntime().availableProcessors() * percentLimit).toInt())

        // GridFS threshold for storing in GridFS vs regular document
        // Ensuring a minimum threshold of 1024 bytes
        val gridFsThreshold = environment.getProperty("encryptable.gridfs.threshold", String.EMPTY).toIntOrNull() ?: 0
        this.gridFsThreshold = maxOf(1024, gridFsThreshold)

        // Should integrity checks be performed on Encryptable entities?
        // Default is true
        // Can be disabled for performance reasons, not recommended.
        this.integrityCheck = environment.getProperty("encryptable.integrity.check", "true").toBoolean()
    }
}