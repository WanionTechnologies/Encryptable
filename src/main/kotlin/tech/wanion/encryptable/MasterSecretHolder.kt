package tech.wanion.encryptable

import org.springframework.core.env.Environment
import tech.wanion.encryptable.util.extensions.copy
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.requireNull

/**
 * Holds the master secret used for encryption and decryption.
 * The master secret is loaded from the application environment or configuration.
 *
 * The master secret can only be set once; subsequent attempts to set it will result in an exception.
 *
 * Usage:
 * - To set the master secret (only once):
 *   MasterSecretHolder.setMasterSecret("your-master-secret")
 *
 * - To retrieve the master secret:
 *   val secret = MasterSecretHolder.getMasterSecret()
 *
 * **REMEMBER:** Keep the master secret secure and do not expose it in logs or error messages.
 *
 * The master secret is used only for entities whose `id` is annotated with `@Id`.
 * Entities with `@HKDFId`-annotated ids derive their own unique keys and do not use the master secret at all; they are completely independent of it.
 */
object MasterSecretHolder {
    /* Master secret used for encryption and decryption. */
    private var masterSecret: String? = null

    init {
        val environment = getBean(Environment::class.java)

        // Load the master secret from environment variable or configuration
        masterSecret = environment.getProperty("encryptable.master.secret")
    }

    /**
     * Sets the master secret if it is not already set.
     * @param secret The master secret to set.
     * @throws IllegalStateException if the master secret is already set.
     */
    fun setMasterSecret(secret: String?) {
        requireNull(masterSecret) { "Master secret is already set and cannot be changed." }
        masterSecret = secret
    }

    /**
     * Retrieves a copy of the master secret.
     * if it wasn't a copy, the Master Secret would be zerified at the request end, making it unusable for further en/decryption operations.
     * @return A copy of the master secret.
     * @throws IllegalStateException if the master secret is not set.
     */
    fun getMasterSecret(): String = masterSecret?.copy() ?: throwMasterSecretNotSet()

    /**
     * Checks if the master secret is set.
     * @return True if the master secret is set.
     * @throws IllegalStateException if the master secret is not set.
     */
    fun masterSecretIsSet(): Boolean {
        if (masterSecret == null)
            throwMasterSecretNotSet()
        return true
    }

    /** Throws an exception indicating the master secret is not set. */
    private fun throwMasterSecretNotSet(): Nothing = throw IllegalStateException("Master secret is not set.")
}