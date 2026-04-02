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
     * Default is 16384 bytes (16KB). Can be configured down to a minimum of 1024 bytes (1KB).
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

    /**
     * Controls how [tech.wanion.encryptable.CID.toString] renders a CID.
     * When true (default), CIDs are rendered as standard Base64 with padding — the same format
     * MongoDB Compass displays for BSON Binary subtype 0x03 fields, making it easier to copy/paste
     * values directly between your logs and Compass.
     * When false, CIDs are rendered as URL-safe Base64 without padding (the native CID format).
     * See `encryptable.cid.base64` property.
     */
    val cidBase64: Boolean

    init {
        val environment = getBean(Environment::class.java)

        // Thread limit for parallel encryption/decryption operations
        // Default is number of available processors
        // better documentated at `tech.wanion.encryptable.util.Limited`
        val percentLimit = minOf(environment.getProperty("thread.limit.percentage", String.EMPTY).toFloatOrNull() ?: 0.38f, 1.0f)
        this.threadLimit = maxOf(1, (Runtime.getRuntime().availableProcessors() * percentLimit).toInt())

        // Storage threshold for routing ByteArray fields to external storage vs inline document.
        // Default is 16384 bytes (16KB) when not configured.
        // Can be lowered to a minimum of 1024 bytes (1KB) for cost-optimized external storage backends (e.g. S3, R2).
        val configuredThreshold = environment.getProperty("encryptable.storage.threshold", String.EMPTY).toIntOrNull()
        this.storageThreshold = if (configuredThreshold != null) maxOf(1024, configuredThreshold) else 16384

        // Should integrity checks be performed on Encryptable entities?
        // Default is true
        // Can be disabled for performance reasons, not recommended.
        this.integrityCheck = environment.getProperty("encryptable.integrity.check", "true").toBoolean()

        // Is the application currently performing a migration process?
        // This can be used to conditionally disable certain features or checks during migration for performance reasons.
        // Default is false, and should be set to true during migration processes.
        this.migration = environment.getProperty("encryptable.migration", "false").toBoolean()

        // Controls CID.toString() rendering format.
        // Default is true: render as standard Base64 with padding, matching what MongoDB Compass displays for BSON Binary custom subtype 128 fields.
        // Set to false to use URL-safe Base64 without padding (native format).
        this.cidBase64 = environment.getProperty("encryptable.cid.base64", "true").toBoolean()
    }
}