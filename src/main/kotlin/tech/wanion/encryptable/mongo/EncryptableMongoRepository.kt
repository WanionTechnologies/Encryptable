package tech.wanion.encryptable.mongo

import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.NoRepositoryBean
import java.util.*

/**
 * # EncryptableMongoRepository
 *
 * Base interface for MongoDB repositories that support Encryptable entities.
 *
 * Extends `MongoRepository` and adds methods for secret-based access and encryption lifecycle.
 *
 * ## Type Parameters
 * - `T`: The type of `Encryptable` entity managed by the repository.
 */
@NoRepositoryBean
interface EncryptableMongoRepository<T: Encryptable<T>> : MongoRepository<T, CID> {
    /**
     * Gets the class type of the entity managed by the repository.
     *
     * ### Returns
     * - The entity class (`Class<T>`).
     */
    fun getTypeClass(): Class<T>

    /**
     * Gets the `MongoOperations` instance for direct database access.
     *
     * ### Returns
     * - The `MongoOperations` instance.
     */
    fun getMongoOperations(): MongoOperations

    /**
     * # markForCleanup
     *
     * Marks a newly created entity for conditional cleanup at request end.
     *
     * The entity registers itself with the repository for tracking. **Cleanup only occurs if the
     * entity remains unpersisted** - if `save()` is called before the request ends, the entity
     * is persisted normally and cleanup is skipped.
     *
     * If the entity is **still unsaved by request completion**, it will be automatically cascade-deleted
     * to prevent orphaned resources (GridFS files, child entities, etc.) from lingering in the database.
     *
     * This pattern ensures temporary entities created during request processing don't leave
     * orphaned data if they're ultimately not needed.
     *
     * ## Parameters
     * - `entity`: The new entity to mark for cleanup.
     *
     * ## Throws
     * - `IllegalArgumentException` if the entity is not new (already persisted).
     */
    fun markForCleanup(entity: T)

    /**
     * Rotates secrets for entity matching the old secret to the new secret.
     *
     * ### Parameters
     * - `oldSecret`: The current secret string.
     * - `newSecret`: The new secret string to rotate to.
     */
    fun rotateSecret(oldSecret: String, newSecret: String)

    /**
     * Checks if an entity exists by its secret string.
     *
     * ### Parameters
     * - `secret`: The secret string.
     *
     * ### Returns
     * - `true` if the entity exists, `false` otherwise.
     */
    fun existsBySecret(secret: String): Boolean

    /**
     * Filters the given secrets to return only those that exist in the repository.
     *
     * ### Parameters
     * - `secrets`: Iterable of secret strings to filter.
     *
     * ### Returns
     * - List of secrets that exist in the repository (`List<String>`).
     *
     * ### Usage Notes
     * - If the returned list has the same length as the input, all secrets exist.
     * - If the returned list is empty, none of the secrets exist.
     * - Otherwise, only some of the secrets exist (partial match).
     */
    fun filterExistingSecrets(secrets: Iterable<String>): List<String>

    /**
     * Filters the given secrets to return only those that do NOT exist in the repository.
     *
     * ### Parameters
     * - `secrets`: Iterable of secret strings to filter.
     *
     * ### Returns
     * - Set of secrets that do not exist in the repository (`Set<String>`).
     *
     * ### Usage Notes
     * - If the returned set has the same size as the input, none of the secrets exist.
     * - If the returned set is empty, all secrets exist.
     * - Otherwise, only some of the secrets are missing (partial match).
     */
    fun filterNonExistingSecrets(secrets: Iterable<String>): Set<String>

    /**
     * Finds an entity by its secret string.
     *
     * ### Parameters
     * - `secret`: The secret string.
     * - `secretAsId`: If true, allows lookup using the Secret as if it is a representation of CID. (like in @Id strategy).
     *
     * ### Returns
     * - `Optional<T>` containing the entity if found.
     */
    fun findBySecret(secret: String, secretAsId: Boolean = false): Optional<T>

    /**
     * Finds an entity by its secret string, or returns `null` if not found.
     *
     * ### Parameters
     * - `secret`: The secret string.
     * - `secretAsId`: If true, allows lookup using the Secret as if it is a representation of CID. (like in @Id strategy).
     *
     * ### Returns
     * - The entity if found, or `null`.
     */
    fun findBySecretOrNull(secret: String, secretAsId: Boolean = false): T? = findBySecret(secret, secretAsId).orElse(null)

    /**
     * Finds all entities matching the given list of secret strings.
     *
     * ### Parameters
     * - `secrets`: Iterable of secret strings.
     * - `secretsAsIds`: If true, allows lookup using the Secrets as if they are representations of CIDs. (like in @Id strategy).
     *
     * ### Returns
     * - List of matching entities (`List<T>`).
     */
    fun findBySecrets(secrets: Iterable<String>, secretsAsIds: Boolean = false): List<T>

    /**
     * Deletes an entity by its secret string.
     *
     * ### Parameters
     * - `secret`: The secret string.
     * - `secretAsId`: If true, allows lookup using the Secret as if it is a representation of CID. (like in @Id strategy).
     */
    fun deleteBySecret(secret: String, secretAsId: Boolean = false)

    /**
     * Deletes all entities matching the given list of secret strings.
     *
     * ### Parameters
     * - `secrets`: Iterable of secret strings.
     * - `secretsAsIds`: If true, allows lookup using the Secrets as if they are representations of CIDs. (like in @Id strategy).
     */
    fun deleteBySecrets(secrets: Iterable<String>, secretsAsIds: Boolean = false)

    /**
     * Updates the metadata for a given entity.
     *
     * This method should be called whenever a byte-array was lazy loaded.
     * It updates the internal tracking information to reflect the new field hash and version.
     *
     * ### Parameters
     * - `entity`: The entity instance that has been modified.
     * - `newFieldHash`: A pair containing the field name (String) and its hash (Int).
     */
    fun updateEntityInfo(entity: Encryptable<T>, newFieldHash: Pair<String, Int>)

    /**
     * Removes the tracking information for a given entity by its CID.
     *
     * This is typically called when an entity is deleted or no longer needs to be tracked.
     *
     * ### Parameters
     * - `cid`: The CID of the entity to remove tracking for.
     */
    fun removeEntityInfo(cid: CID)

    /**
     * Clears thread-local storage, removing any tracked new entities and other per-request data.
     *
     * Should be called at the end of a request or operation context to prevent memory leaks.
     */
    fun clearThreadLocal()

    /**
     * # flushThenClear
     *
     * Flushes all tracked entity changes to the database and clears the tracking maps.
     * This ensures that all modifications are persisted and any orphaned resources from unsaved new entities are cleaned up.
     */
    fun flushThenClear()
}