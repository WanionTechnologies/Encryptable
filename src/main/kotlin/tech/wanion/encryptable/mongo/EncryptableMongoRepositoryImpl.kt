package tech.wanion.encryptable.mongo

import com.mongodb.ReadPreference
import org.bson.Document
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.repository.query.MongoEntityInformation
import org.springframework.data.mongodb.repository.support.CrudMethodMetadata
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository
import org.springframework.data.repository.query.FluentQuery
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.Assert
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.mongo.CID.Companion.binary
import tech.wanion.encryptable.mongo.CID.Companion.cid
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.getField
import tech.wanion.encryptable.util.extensions.markForWiping
import java.lang.reflect.Method
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.Collections.synchronizedList
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * # EncryptableMongoRepositoryImpl
 *
 * Implementation of `EncryptableMongoRepository` for managing `Encryptable` entities in MongoDB.
 *
 * Handles secret-based access, entity initialization, and repository lifecycle.
 *
 * > **Note:** Change detection is fully automatic for all persisted fields, including child entities. However, if you use custom objects as persisted fields, those custom objects must implement proper hashCode and equals methods to ensure correct change detection.
 *
 * ## Type Parameters
 * - `T`: The type of `Encryptable` entity managed by the repository.
 */
@Suppress("UNCHECKED_CAST")
open class EncryptableMongoRepositoryImpl<T: Encryptable<T>>(
    entityInformation: MongoEntityInformation<Any, String>,
    @JvmField val mongoOperations: MongoOperations,
) : SimpleMongoRepository<T, CID>(entityInformation as MongoEntityInformation<T, CID>, mongoOperations), EncryptableMongoRepository<T> {
    private companion object {
        private val logger = LoggerFactory.getLogger(EncryptableMongoRepositoryImpl::class.java)

        /** ID
         *
         * The MongoDB field name for the entity ID.
         *
         * **Type:** `String`
         */
        private const val MONGO_ID_FIELD = "_id"

        /**
         * **prepareMethod**
         *
         * Reflection handle for the private `prepare()` method in `Encryptable`.
         *
         * - **Internal usage only:** Used by repository logic to initialize entities via reflection.
         * - The method signature includes only a Continuation parameter, as required for Kotlin suspend functions.
         * - This `Method` is cached for performance and should not be used outside repository internals.
         *
         * **Type:** `Method`
         */
        val prepareMethod: Method = Encryptable::class.java.getDeclaredMethod(
            "prepare"
        ).apply { isAccessible = true }

        /**
         * **restoreMethod**
         *
         * Reflection handle for the private `restore(secret: String)` method in `Encryptable`.
         *
         * - **Internal usage only:** Used by repository logic to decrypt entities via reflection.
         * - The method signature includes a `String` (secret) and a Continuation parameter, as required for Kotlin suspend functions.
         * - This `Method` is cached for performance and should not be used outside repository internals.
         *
         * **Type:** `Method`
         */
        val restoreMethod: Method = Encryptable::class.java.getDeclaredMethod(
            "restore",
            String::class.java
        ).apply { isAccessible = true }

        /**
         * **integrityCheckAndCleanUpMethod**
         *
         * Reflection handle for the private `integrityCheckAndCleanUp()` method in `Encryptable`.
         *
         * - Provides reflective access to the integrity check and cleanup logic for Encryptable entities.
         * - When invoked, it checks for broken references and cleans them up to maintain data integrity.
         * - Used internally by repository logic to trigger integrity checks via reflection.
         * - Should not be used outside repository internals.
         *
         * **Type:** `Method`
         */
        val integrityCheckAndCleanUpMethod: Method = Encryptable::class.java.getDeclaredMethod(
            "integrityCheckAndCleanUp",
        ).apply { isAccessible = true }

        /**
         * **updateMethod**
         *
         * Reflection handle for the private suspend `update(secret: String)` method in `Encryptable`.
         *
         * - **Internal usage only:** Used by repository logic to invoke entity update via reflection.
         * - The method signature includes a `String` (secret) and a Continuation parameter, as required for Kotlin suspend functions.
         * - This `Method` is cached for performance and should not be used outside repository internals.
         *
         * **Type:** `Method`
         */
        val updateMethod: Method = Encryptable::class.java.getDeclaredMethod(
            "update"
        ).apply { isAccessible = true }

        /**
         * **prepareForRotationMethod**
         *
         * Reflection handle for the private `prepareForRotation()` method in `Encryptable`.
         *
         * - Provides reflective access to the secret rotation preparation logic for Encryptable entities.
         * - When invoked, it prepares the entity for secret rotation by loading ByteArrays fields from GridFs then deleting these old entries.
         *   *Why?* the GridFs files will be encrypted with the new secret upon saving after rotation.
         * - Used internally by repository logic to trigger secret rotation via reflection.
         * - Should not be used outside repository internals.
         *
         * **Type:** `Method`
         */
        val beforeRotationMethod: Method = Encryptable::class.java.getDeclaredMethod(
            "beforeRotation"
        ).apply { isAccessible = true }

        /**
         * **cascadeDeleteMethod**
         *
         * Reflection handle for the private `cascadeDelete()` method in `Encryptable`.
         *
         * - Provides reflective access to the cascade delete logic for Encryptable entities.
         * - When invoked, it performs a cascading delete operation according to the "part-of" relationship rules:
         *   - Deletes all GridFS files referenced by the entity, regardless of `@PartOf` annotation.
         *   - Deletes child entities in `encryptableListFieldMap` and `encryptableFieldMap` only if their fields are annotated with `@PartOf`.
         * - Used internally by repository logic to trigger cascade deletion via reflection.
         * - Should not be used outside repository internals.
         *
         * **Type:** `Method`
         */
        val cascadeDeleteMethod: Method = Encryptable::class.java.getDeclaredMethod(
            "cascadeDelete"
        ).apply { isAccessible = true }
    }
    /**
     * The entity information and type class for the repository.
     */
    private val entityInformation: MongoEntityInformation<T, CID> = entityInformation as MongoEntityInformation<T, CID>

    /**
     * The MongoDB collection associated with the entity type.
     */
    private val collection = mongoOperations.getCollection(entityInformation.collectionName)

    /**
     * Metadata for the current CRUD method, if available.
     * Used to determine read preferences for queries.
     * May be null if not set by the calling context.
     */
    private val crudMethodMetadata: CrudMethodMetadata? = this.getField("crudMethodMetadata")

    /**
     * Tracks metadata for entities loaded within the current thread context.
     *
     * Uses InheritableThreadLocal to ensure child threads inherit the same tracking context,
     * which is necessary when repository operations spawn new threads.
     *
     * Stores EntityInfo wrappers containing:
     * - The entity instance
     * - Initial hashCode for change detection (dirty checking)
     *
     * This enables automatic persistence of modified entities without explicit save calls.
     * Always call clear() after usage to prevent memory leaks.
     */
    private val entityInfoMapThreadLocal = InheritableThreadLocal<MutableMap<CID, EntityInfo<T>>>()

    /**
     * Tracks newly created entities pending persistence within the current thread context.
     *
     * Entities still marked as "new" at request completion are cascade-deleted to prevent
     * orphaned resources (GridFS files, child entities) and maintain referential integrity.
     */
    private val newEntitiesThreadLocal = InheritableThreadLocal<MutableList<T>>()

    /**
     * Metadata for the Encryptable entity type managed by this repository.
     *
     * Contains cached information about encrypted fields and the ID generation strategy (standard or HKDF).
     * Used to optimize reflection, encryption, decryption, and deterministic ID calculation for entities.
     * This metadata is initialized once per repository instance and reused for all operations involving the entity type.
     *
     * **Type:** `Encryptable.Metadata`
     */
    private lateinit var metadata: Encryptable.Metadata

    @JvmField
    val typeClass: Class<T>

    init {
        val entityMetadata = entityInformation.getField<MongoPersistentEntity<T>>("entityMetadata")
        this.typeClass = entityMetadata.type
    }

    /**
     * # getEntityInfoMap
     *
     * Retrieves the thread-local map of tracked entities and their initial hash codes.
     * Initializes the map if it does not already exist for the current thread.
     */
    private fun getEntityInfoMap(): MutableMap<CID, EntityInfo<T>> {
        return entityInfoMapThreadLocal.get() ?: ConcurrentHashMap<CID, EntityInfo<T>>().also { entityInfoMapThreadLocal.set(it) }
    }

    /**
     * # getNewEntityList
     *
     * Retrieves the thread-local list of newly created entities pending persistence.
     * Initializes the list if it does not already exist for the current thread.
     */
    private fun getNewEntityList(): MutableList<T> {
        return newEntitiesThreadLocal.get() ?: synchronizedList(mutableListOf<T>()).also { newEntitiesThreadLocal.set(it) }
    }

    /**
     * # getTypeClass
     *
     * Gets the class type of the entity managed by the repository.
     *
     * ## Returns
     * - The entity class (`Class<T>`).
     */
    override fun getTypeClass(): Class<T> = typeClass

    /**
     * # getMongoOperations
     *
     * Gets the `MongoOperations` instance for direct database access.
     *
     * ## Returns
     * - The `MongoOperations` instance.
     */
    override fun getMongoOperations(): MongoOperations = mongoOperations

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
     * to prevent orphaned resources (GridFS files, child entities, etc.).
     *
     * ## Parameters
     * - `entity`: The new entity to mark for cleanup.
     *
     * ## Throws
     * - `IllegalArgumentException` if the entity is not new.
     */
    override fun markForCleanup(entity: T) {
        require(entity.isNew()) { "Only new entities can be marked for cleanup." }
        getNewEntityList().add(entity)
    }

    /**
     * # rotateSecret
     *
     * Rotates the secret for a **single @HKDFId entity**, re-encrypting all data and associated resources (including GridFS files) with the new secret.
     *
     * **Important:** This method is **only supported for @HKDFId entities**. @Id entities do not support per-entity secret rotation
     * because they use non-secret IDs and rely on the master secret for encryption (if @Encrypt is used).
     *
     * ## What This Method Does
     *
     * This method performs secure, atomic secret rotation for a single entity:
     * 1. **Validates** that the old secret exists and the new secret is not already in use (prevents data loss)
     * 2. **Finds** the entity by the old secret and invokes pre-rotation hooks
     * 3. **Removes** the old entity from the database (with its old CID derived from old secret)
     * 4. **Re-encrypts** all encrypted fields and GridFS files with keys derived from the new secret
     * 5. **Derives new CID** from the new secret using HKDF
     * 6. **Saves** the entity with the new CID and re-encrypted data
     * 7. **Audit logs** the rotation event (without exposing actual secrets)
     * 8. **Marks secrets for memory clearing** at the end of the request (transient knowledge)
     *
     * ## Entity Type Restrictions
     *
     * - ✅ **@HKDFId entities:** Supported - Each entity has its own secret, rotation is per-entity
     * - ❌ **@Id entities:** Not supported - Would require master secret rotation (system-wide operation)
     *
     * ## Parameters
     * - `oldSecret`: The current secret string used to access the entity. Will be marked for memory clearing.
     * - `newSecret`: The new secret string to rotate to. Must not already be in use. Will be marked for memory clearing.
     *
     * ## Behavior
     * - If no entity is found with the old secret, throws `IllegalArgumentException` and aborts
     * - If an entity already exists with the new secret, throws `IllegalArgumentException` to prevent permanent data loss
     * - All encrypted fields and associated resources (GridFS files) are re-encrypted with the new secret
     * - The entity's CID changes (derived from new secret via HKDF)
     * - The old CID can no longer be used to access this entity
     * - Both `oldSecret` and `newSecret` are automatically registered for memory clearing at request end
     *
     * ## Performance Considerations
     * - This is a heavyweight operation (decrypt all fields → re-encrypt all fields → re-save)
     * - GridFS files are re-encrypted if present (additional I/O)
     * - For large entities or many GridFS files, this can take several seconds
     * - Consider user feedback/progress indication for long-running rotations
     *
     * ## Security Notes
     * - **Per-entity operation:** This rotates the secret for ONE entity only, not system-wide
     * - **Not master secret rotation:** This does not affect the master secret used by @Id entities
     * - **Transient knowledge maintained:** Secrets are cleared from memory after the request completes
     * - **Audit trail:** All rotation attempts (success/failure) are logged without leaking secrets
     * - **Atomic operation:** Wrapped in @Transactional - either completes fully or rolls back
     *
     * ## Best Practices
     * - **Transactional context:** Already wrapped in @Transactional, but ensure your calling code doesn't break the transaction
     * - **Bulk rotation:** Invoke this method for each entity individually (do not batch)
     * - **User-initiated only:** This should be called when the user explicitly requests rotation (e.g., password change)
     * - **Handle failures gracefully:** Wrap in try-catch and provide clear user feedback
     *
     * ## Example Usage
     * ```kotlin
     * // User password change flow
     * @PostMapping("/change-password")
     * fun changePassword(@RequestBody request: PasswordChangeRequest) {
     *     val oldSecret = deriveSecret(request.username, request.oldPassword, request.old2FA)
     *     val newSecret = deriveSecret(request.username, request.newPassword, request.new2FA)
     *
     *     try {
     *         userRepository.rotateSecret(oldSecret, newSecret)
     *         return ResponseEntity.ok("Password changed successfully")
     *     } catch (e: IllegalArgumentException) {
     *         return ResponseEntity.badRequest().body("Rotation failed: ${e.message}")
     *     }
     * }
     * ```
     *
     * ## Related Operations
     * - For @Id entities with @Encrypt fields, see documentation on master secret rotation complexity
     *
     * @param oldSecret The current secret used to access the entity
     * @param newSecret The new secret to rotate to (must not already exist)
     * @throws UnsupportedOperationException if called on an @Id entity (only @HKDFId supported)
     * @throws IllegalArgumentException if no entity found with old secret or if new secret already exists
     * @throws Exception if rotation fails (e.g., database error, encryption failure) - transaction will roll back
     */
    @Transactional
    override fun rotateSecret(oldSecret: String, newSecret: String) {
        // oldSecret and newSecret should be cleared at the end of the request to limit exposure in memory.
        markForWiping(oldSecret, newSecret)

        if (metadata.strategies == Encryptable.Metadata.Strategies.ID)
            throw UnsupportedOperationException("Secret rotation is not supported for entities using standard ID strategy. Use HKDF strategy for secret rotation support.")

        val startTime = Instant.now()
        val ip = EncryptableContext.getRequestIP()

        // Find entity by old secret
        val encryptable = findBySecretOrNull(oldSecret)

        if (encryptable == null) {
            // Audit log: rotation failed - entity not found
            logger.warn(
                "Secret rotation failed. " +
                "EntityType: ${typeClass.simpleName}, " +
                "IP: $ip, " +
                "Timestamp: $startTime, " +
                "Reason: No entity found with provided secret"
            )
            throw IllegalArgumentException("No entity found with the old secret. rotation aborted.")
        }

        val entityId = encryptable.id

        // Check if new secret already in use
        if (existsBySecret(newSecret)) {
            // Audit log: rotation failed - new secret collision
            logger.warn(
                "Secret rotation failed. " +
                "EntityType: ${typeClass.simpleName}, " +
                "EntityID: $entityId, " +
                "IP: $ip, " +
                "Reason: New secret conflicts with existing entity"
            )
            throw IllegalArgumentException("An entity with the new secret already exists. rotation aborted to prevent permanent data loss.")
        }

        // Audit log: rotation initiated
        logger.info(
            "Secret rotation initiated. " +
            "EntityType: ${typeClass.simpleName}, " +
            "EntityID: $entityId, " +
            "IP: $ip, "
        )

        try {
            // Perform rotation
            val id = encryptable.id ?: throw IllegalStateException("Entity must have an ID to rotate secret.")
            val query = Query(Criteria.where(entityInformation.idAttribute).`is`(id))
            mongoOperations.remove(query, entityInformation.javaType)
            beforeRotationMethod.invoke(encryptable)
            removeEntityInfo(id)
            encryptable.withSecret(newSecret)
            save(encryptable)

            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime).toMillis()

            // Audit log: rotation successful
            logger.info(
                "Secret rotation completed successfully. " +
                "EntityType: ${typeClass.simpleName}, " +
                "EntityID: $entityId, " +
                "IP: $ip, " +
                "Duration: ${duration}ms, "
            )
        } catch (e: Exception) {
            val endTime = Instant.now()
            val duration = Duration.between(startTime, endTime).toMillis()

            // Audit log: rotation failed with exception
            logger.error(
                "Secret rotation failed with exception. " +
                "EntityType: ${typeClass.simpleName}, " +
                "EntityID: $entityId, " +
                "IP: $ip, " +
                "Duration: ${duration}ms, " +
                "Error: ${e.javaClass.simpleName}, " +
                "Message: ${e.message}"
            )

            // Re-throw to maintain transaction rollback behavior
            throw e
        }
    }

    /**
     * # getReadPreference
     *
     * Retrieves the read preference from the current CRUD method metadata, if available.
     * This allows queries to respect any read preference specified in the calling context.
     *
     * ## Returns
     * - `Optional<ReadPreference>` containing the read preference if set, or empty if not available.
     */
    private fun getReadPreference(): Optional<ReadPreference> {
        if (crudMethodMetadata == null) {
            return Optional.empty()
        }
        return crudMethodMetadata.readPreference
    }

    /**
     * # save
     *
     * Saves a new entity to the repository. Calls `init()` on the entity before inserting.
     * Throws if the entity is not new.
     *
     * ## Why `save` over `insert`?
     * Semantically, `save` was chosen because it previously represented the action of saving an entity.
     * With the repository now auto-updating existing entities, updates are handled automatically and do not require user interaction.
     * Therefore, `save` is only used for inserting new entities, and `update` is reserved for automatic updates of existing entities.
     * This maintains clarity and avoids redundant or confusing API methods.
     *
     * ## Parameters
     * - `entity`: The entity to save (must be new).
     *
     * ## Returns
     * - The saved entity.
     */
    override fun <S : T> save(entity: S): S {
        Assert.notNull(entity, "Entity must not be null")
        if (entity.isNew()) {
            prepareMethod.invoke(entity)
            return mongoOperations.insert(entity)
        }
        return entity
    }

    /**
     * # saveAll
     *
     * Saves multiple new entities to the repository. Calls `init()` on each entity before inserting.
     * Throws if any entity is not new.
     *
     * ## Why `saveAll` over `insert`?
     * The choice of `saveAll` follows the same reasoning as `save`: only new entities should be inserted explicitly.
     * Updates to existing entities are performed automatically by the repository, without user interaction.
     * This keeps the API clear and focused, with `saveAll` dedicated to inserting new entities only.
     *
     * ## Parameters
     * - `entities`: The entities to save (all must be new).
     *
     * ## Returns
     * - List of saved entities.
     */
    override fun <S : T> saveAll(entities: Iterable<S>): List<S> {
        Assert.notNull(entities, "The given Iterable of entities not be null")
        val newEntities = entities.filter { entityInformation.isNew(it) }.toList()
        if (newEntities.isEmpty())
            return emptyList()
        newEntities.parallelForEach { prepareMethod.invoke(it) }
        val savedEntities = mongoOperations.insertAll(newEntities)
        return ArrayList(savedEntities)
    }

    /**
     * # existsBySecret
     *
     * Checks if an entity exists by its secret.
     *
     * ## Parameters
     * - `secret`: The secret string.
     *
     * ## Returns
     * - `true` if the entity exists, `false` otherwise.
     */
    override fun existsBySecret(secret: String): Boolean {
        // secret should be cleared at the end of the request to limit exposure in memory.
        markForWiping(secret)
        return existsById(metadata.strategies.getIDFromSecret(secret, typeClass))
    }

    /**
     * # queryExistingCids
     *
     * Private helper method to query which CIDs exist in the repository from the given secrets.
     *
     * ## Parameters
     * - `secrets`: Set of secret strings to check.
     *
     * ## Returns
     * - Pair containing:
     *   - First: Map from CID to secret (all input secrets)
     *   - Second: Set of CIDs that exist in the repository
     */
    private fun queryExistingCids(secrets: Set<String>): Pair<Map<CID, String>, Set<CID>> {
        // secrets should be cleared at the end of the request to limit exposure in memory.
        markForWiping(secrets)

        if (secrets.isEmpty())
            return emptyMap<CID, String>() to emptySet()

        val cidToSecretMap = secrets.associateBy { secret ->
            when (secret.length) {
                22 -> secret.cid // will throw if not valid Base64 URL Safe CID
                else -> metadata.strategies.getIDFromSecret(secret, typeClass)
            }
        }
        val cidBinaries = cidToSecretMap.keys.map { it.binary }
        val filter = Document(MONGO_ID_FIELD, Document($$"$in", cidBinaries))
        val projection = Document(MONGO_ID_FIELD, 1)
        val cursor = collection.find(filter).projection(projection)
        val existingCids = Collections.synchronizedSet(mutableSetOf<CID>())
        cursor.forEach { doc ->
            val binary = doc.get(MONGO_ID_FIELD) as? Binary ?: return@forEach
            existingCids.add(binary.cid)
        }
        return cidToSecretMap to existingCids
    }

    /**
     * # filterExistingSecrets
     *
     * Filters the given secrets to return only those that exist in the repository.
     *
     * ## Parameters
     * - `secrets`: Iterable of secret strings to filter.
     *
     * ## Returns
     * - List of secrets that exist in the repository (`List<String>`).
     *
     * ## Usage Notes
     * - If the returned list has the same length as the input, all secrets exist.
     * - If the returned list is empty, none of the secrets exist.
     * - Otherwise, only some of the secrets exist (partial match).
     */
    override fun filterExistingSecrets(secrets: Iterable<String>): List<String> {
        val inputSet = secrets.toSet()
        val (cidToSecretMap, existingCids) = queryExistingCids(inputSet)
        return existingCids.map { cidToSecretMap.getValue(it) }
    }

    /**
     * # filterNonExistingSecrets
     *
     * Filters the given secrets to return only those that do NOT exist in the repository.
     *
     * ## Parameters
     * - `secrets`: Iterable of secret strings to filter.
     *
     * ## Returns
     * - Set of secrets that do not exist in the repository (`Set<String>`).
     *
     * ## Usage Notes
     * - If the returned set has the same size as the input, none of the secrets exist.
     * - If the returned set is empty, all secrets exist.
     * - Otherwise, only some of the secrets are missing (partial match).
     */
    override fun filterNonExistingSecrets(secrets: Iterable<String>): Set<String> {
        val inputSet = secrets.toSet()
        val (cidToSecretMap, existingCids) = queryExistingCids(inputSet)
        val nonExistingCids = cidToSecretMap.keys - existingCids
        return nonExistingCids.mapTo(HashSet()) { cidToSecretMap.getValue(it) }
    }

    /**
     * # findBySecret
     *
     * Finds an entity by its secret string and stores the entity and its initial hashCode for change detection.
     * If the entity contains encrypted fields, it is automatically decrypted, ensuring the returned entity is ready for use.
     * The secret is stored in the entity to support lazy loading and automatic updates.
     * For entities with large ByteArray fields, content is automagically loaded via the aspect when accessed, so manual loading is not required.
     *
     * ## Parameters
     * - `secret`: The secret string used to deterministically generate the entity's CID.
     * - `secretAsId`: If true, allows lookup using the Secret as if it is a representation of CID. (like in @Id strategy).
     *
     * ## Returns
     * - `Optional<T>` containing the entity if found, or empty if not found.
     */
    override fun findBySecret(secret: String, secretAsId: Boolean): Optional<T> {
        // secret should be cleared at the end of the request to limit exposure in memory.
        // the restore method already does this, but we're doing it here as well just to be safe.
        markForWiping(secret)

        // Determine CID from secret
        val id = resolveCid(secret, secretAsId)

        val entityInfoMap = getEntityInfoMap()
        val query = Query(Criteria.where(entityInformation.idAttribute).`is`(id))
        getReadPreference().ifPresent { readPreference: ReadPreference? ->
            query.withReadPreference(readPreference!!)
        }
        val entityOpt = Optional.ofNullable(mongoOperations.findOne(query, entityInformation.javaType))
        if (entityOpt.isPresent) {
            // If secretAsId is true and the secret is a valid CID and the entity is NOT an @Id entity.
            // return it without restoring as it was looked up by its ID directly.
            if (secretAsId && secret == id.toString() && metadata.strategies != Encryptable.Metadata.Strategies.ID)
                return entityOpt
            val entity = entityOpt.get()
            // Decrypt entity using the provided secret
            restoreMethod.invoke(entity, secret)
            // Track initial hashes and register entity for later change detection;
            entityInfoMap[id] = EntityInfo(entity, entity.hashCodes())
            // Check and clean up any broken reference.
            if (EncryptableConfig.integrityCheck)
                integrityCheck(entity)
            // touch() to update audit fields
            entity.touch()
        }
        return entityOpt
    }

    /**
     * # findAllBySecrets
     *
     * Finds all entities matching the given list of secret strings.
     *
     * For each secret, this method deterministically converts it to its CID using the repository's id strategy.
     * It then creates a map from CID to secret for all input secrets, which can be used for change tracking,
     * decryption, or other logic that requires knowing the original secret for each entity.
     *
     * The method queries MongoDB for all entities whose CID matches any of the provided secrets.
     *
     * ## Parameters
     * - `secrets`: Iterable of secret strings to search for.
     * - `secretsAsIds`: If true, allows lookup using the Secrets as if they are representations of CIDs. (like in @Id strategy).
     *
     * ## Returns
     * - List of entities whose CID matches any of the provided secrets. Returns an empty list if none found.
     */
    override fun findBySecrets(secrets: Iterable<String>, secretsAsIds: Boolean): List<T> {
        // secrets should be cleared at the end of the request to limit exposure in memory.
        // the restore method already does this, but we're doing it here as well just to be safe.
        markForWiping(secrets)

        // Cid to original secret map
        val cidToOriginalSecret = mutableMapOf<CID, String>()

        // Map secrets to CIDs
        val cidToSecretMap = secrets.associateBy { secret ->
            val id = resolveCid(secret, secretsAsIds)
            cidToOriginalSecret[id] = secret
            id
        }

        if (cidToSecretMap.isEmpty())
            return emptyList()
        val query = Query(Criteria.where(entityInformation.idAttribute).`in`(cidToSecretMap.keys))
        getReadPreference().ifPresent { readPreference: ReadPreference? ->
            query.withReadPreference(readPreference!!)
        }

        val entities = mongoOperations.find(query, entityInformation.javaType)
        val entityInfoMap = getEntityInfoMap()
        entities.forEach { entity ->
                val id = entity.id as CID
                // If secretsAsIds is true and the original secret is a valid CID and the entity is NOT an @Id entity.
                // do not restore it as it was looked up by its ID directly.
                if (secretsAsIds && cidToOriginalSecret[id] == id.toString() && metadata.strategies != Encryptable.Metadata.Strategies.ID)
                    return@forEach
                restoreMethod.invoke(entity, cidToSecretMap[id])
                // Track initial hashes and register entity for later change detection; touch() may update audit fields
                entityInfoMap[id] = EntityInfo(entity, entity.hashCodes())
                // Check and clean up any broken reference.
                if (EncryptableConfig.integrityCheck)
                    integrityCheck(entity)
                entity.touch()
        }
        entities.filterNotNull()
        return entities
    }

    /**
     * # resolveCid
     *
     * Resolves the CID for a given secret string, optionally interpreting the secret directly as a CID.
     *
     * ## Parameters
     * - `secret`: The secret string to resolve.
     * - `secretAsId`: If true, attempts to interpret the secret directly as a CID if it matches the expected length.
     *
     * ## Returns
     * - The resolved CID.
     */
    private fun resolveCid(secret: String, secretAsId: Boolean): CID {
        // If secretAsId is true, first we try to interpret the secret directly as a CID.
        if (secretAsId && secret.length == 22) {
            try {
                val id = secret.cid
                return id
            }
            catch (_: Exception) {
                // not a valid CID, proceed with normal secret lookup
            }
        }
        return metadata.strategies.getIDFromSecret(secret, typeClass)
    }

    /**
     * # integrityCheck
     *
     * Performs an integrity check on the given entity to identify and clean up any broken references.
     * Logs any missing references found during the integrity check.
     *
     * ## Parameters
     * - `entity`: The entity to perform the integrity check on.
     */
    private fun integrityCheck(entity: T) {
        // Check and clean up any broken references.
        val missingCidsByType = integrityCheckAndCleanUpMethod.invoke(entity) as Map<Class<out Encryptable<*>>, Set<String>>
        // Log any missing references found during integrity check
        missingCidsByType.forEach { (type, missingCids) ->
            logger.warn(" - Missing references of type ${type.simpleName}: $missingCids")
        }
    }

    /**
     * # updateEntityInfo
     *
     * Updates the tracked hashCode for a specific field of an entity.
     * Should be called after lazy loading binary fields, to synchronize the tracked hashCode and prevent unnecessary updates.
     *
     * ## Parameters
     * - `entity`: The entity instance whose hashCode should be updated in the repository's tracking map.
     * - `newFieldHash`: A Pair containing the field name and its new hashCode after lazy loading.
     *
     * ## Throws
     * - `IllegalArgumentException` if the entity is not tracked or does not match the tracked instance.
     */
    override fun updateEntityInfo(entity: Encryptable<T>, newFieldHash: Pair<String, Int>) {
        if (!typeClass.isAssignableFrom(entity.javaClass)) throw IllegalArgumentException("Entity must be of type ${typeClass.name}")
        val entityInfoMap = getEntityInfoMap()
        val cid = entity.id ?: throw IllegalArgumentException("Entity must have a CID set to update secret info.")
        val entityInfo = entityInfoMap[cid] ?:
        // this exception was added to prevent silent failures if the entity was not loaded via findBySecret(secret)
            throw IllegalArgumentException("No entity info found for entity with CID $cid. Ensure the entity was loaded via findBySecret(secret) before updating.")
        // and thus is not tracked for changes. This ensures the caller is aware of the requirement to load
        // the entity via findBySecret(secret) before calling updateEntityInfo.
        if (entityInfo.entity != entity)
            throw IllegalArgumentException("The provided entity does not match the tracked entity for CID $cid. Ensure the same instance is used.")
        entityInfo.initialHashCode[newFieldHash.first] = newFieldHash.second
    }

    /**
     * # removeEntityInfo
     *
     * Removes the entity info tracking for the given entity CID.
     * Should be called when an entity is deleted or has its secret rotated to clean up associated info.
     *
     * ## Parameters
     * - `cid`: The CID of the entity to remove tracking info for.
     */
    override fun removeEntityInfo(cid: CID) {
        getEntityInfoMap().remove(cid)
    }

    /**
     * # deleteBySecret
     *
     * Deletes an entity by its secret string within a transaction. If any part of the deletion fails, the transaction is rolled back and no changes are persisted.
     *
     * ## Parameters
     * - `secret`: The secret string of the entity to delete.
     * - `secretAsId`: If true, allows lookup using the Secret as if it is a representation of CID. (like in @Id strategy).
     *
     * ## Throws
     * - Any exception encountered during deletion, which will cause the transaction to roll back.
     * - **Only applicable if current MongoDb setup supports transactions.**
     */
    @Transactional
    override fun deleteBySecret(secret: String, secretAsId: Boolean) {
        // secret should be cleared at the end of the request to limit exposure in memory.
        // findBySecret already marks it, so no need to do it again here.
        val encryptable = findBySecretOrNull(secret, secretAsId) ?: return
        val query = Query(Criteria.where(entityInformation.idAttribute).`is`(encryptable.id))
        mongoOperations.remove(query, entityInformation.javaType)
        cascadeDeleteMethod.invoke(encryptable)
        removeEntityInfo(encryptable.id!!)
    }

    /**
     * # deleteAllBySecrets
     *
     * Deletes all entities matching the given list of secret strings within a transaction. If any part of the deletion fails, the transaction is rolled back and no changes are persisted.
     *
     * ## Parameters
     * - `secrets`: Iterable of secret strings of the entities to delete.
     * - `secretsAsIds`: If true, allows lookup using the Secrets as if they are representations of CIDs. (like in @Id strategy).
     *
     * ## Throws
     * - Any exception encountered during deletion, which will cause the transaction to roll back.
     * - **Only applicable if current MongoDb setup supports transactions.**
     */
    @Transactional
    override fun deleteBySecrets(secrets: Iterable<String>, secretsAsIds: Boolean) {
        // Process secrets in batches to avoid OOM
        val batchSize = 500
        val secretList = secrets.toList()
        if (secretList.isEmpty()) return
        secretList.chunked(batchSize).forEach { batch ->
            // secrets should be cleared at the end of the request to limit exposure in memory.
            // findBySecrets already registers them, so no need to do it again here.
            val encryptables = findBySecrets(batch, secretsAsIds)
            if (encryptables.isEmpty()) return@forEach
            val cids = encryptables.map { it.id }
            val query = Query(Criteria.where(entityInformation.idAttribute).`in`(cids))
            mongoOperations.remove(query, entityInformation.javaType)
            encryptables.parallelForEach(false) { encryptable ->
                cascadeDeleteMethod.invoke(encryptable)
                removeEntityInfo(encryptable.id!!)
            }
        }
    }

    /**
     * # clearThreadLocal
     *
     * Flushes pending changes and clears thread-local tracking maps to prevent memory leaks.
     * Should be called at the end of each request/operation.
     */
    override fun clearThreadLocal() {
        flushThenClear()
        this.entityInfoMapThreadLocal.remove()
        this.newEntitiesThreadLocal.remove()
    }

    /**
     * # flushThenClear
     *
     * Flushes all tracked entity changes to the database and clears the tracking maps.
     *
     * This method performs the following steps:
     * 1. Iterates over all tracked entities in the thread-local map.
     * 2. Compares the initial and current hash codes to detect changes.
     * 3. For changed entities, invokes the `update` method to persist only modified fields.
     * 4. Iterates over all newly created entities marked for cleanup.
     * 5. If any new entity is still marked as new (unsaved), invokes `cascadeDelete` to clean up resources.
     * 6. Clears both the entity info map and new entities list to prevent memory leaks.
     *
     * This ensures that all modifications are persisted and any orphaned resources from unsaved new entities are cleaned up.
     */
    override fun flushThenClear() {
        val entityInfoMap = getEntityInfoMap().values
        val newEntities = getNewEntityList()
        entityInfoMap.parallelForEach {
            val currentHashCodes = it.entity.hashCodes()
            if (it.initialHashCode == currentHashCodes)
                return@parallelForEach
            val entity = it.entity
            // Entity has changed, perform update
            // We ignore exceptions to ensure all entities are processed.
            try {
                update(entity, it.initialHashCode, currentHashCodes)
            } catch (e: Exception) {
                // log and continue
                e.printStackTrace()
            }
        }
        newEntities.parallelForEach { newEntity ->
            if (!newEntity.isNew()) return@parallelForEach
            // New entity was not saved, perform cascade delete to clean up resources
            try {
                cascadeDeleteMethod.invoke(newEntity)
            } catch (e: Exception) {
                // log and continue
                e.printStackTrace()
            }
        }
        entityInfoMap.clear()
        newEntities.clear()
        clearThreadLocals()
    }

    /**
     * # clearThreadLocals
     *
     * Clears the thread-local maps used for tracking entity info and new entities.
     * This helps prevent memory leaks by removing references to entities after processing is complete.
     */
    private fun clearThreadLocals() {
        entityInfoMapThreadLocal.remove()
        newEntitiesThreadLocal.remove()
    }

    /**
     * Updates only the changed fields of an existing entity in the database.
     *
     * This method:
     * - Checks if the entity is not new (already exists in the database).
     * - Compares the initial and current hash codes to determine which fields have changed.
     * - Invokes the entity's update logic via reflection.
     * - Builds a MongoDB Update object for only the changed fields.
     * - Executes a partial update using mongoOperations.updateFirst, updating only the modified fields.
     *
     * @param entity The entity to update. Must not be null and must already exist in the database.
     * @param initialHashCode The initial hash codes of the entity's fields.
     * @param hashCodes The current hash codes of the entity's fields.
     *
     * @throws UnsupportedOperationException If the entity is new (not yet persisted).
     * @throws IllegalArgumentException If the entity is null.
     */
    private fun <S : T> update(entity: S, initialHashCode: Map<String, Int>, hashCodes: Map<String, Int>) {
        Assert.notNull(entity, "Entity must not be null")
        if (entityInformation.isNew(entity))
            throw UnsupportedOperationException("This method should only be called to update an already existing entity, to insert a new entity, use .save(entity).")
        val changedFields = compareChangedFields(initialHashCode, hashCodes)
        if (changedFields.isEmpty()) return
        updateMethod.invoke(entity)
        val query = Query(Criteria.where("_id").`is`(entity.id))
        val update = Update()
        for ((field, _) in changedFields) {
            val value = entity.metadata.persistedFields[field]?.get(entity)
            update.set(field, value)
        }
        mongoOperations.updateFirst(query, update, entityInformation.javaType)
    }

    /**
     * Compares two hash maps and returns a map of changed fields.
     * @param initialHashCode The initial hash codes of fields.
     * @param hashCodes The current hash codes of fields.
     * @return Map of field names to a Pair of (oldHash, newHash) for changed fields.
     */
    private fun compareChangedFields(initialHashCode: Map<String, Int>, hashCodes: Map<String, Int>): Map<String, Pair<Int, Int>> {
        val changed = mutableMapOf<String, Pair<Int, Int>>()
        for ((key, oldHash) in initialHashCode) {
            val newHash = hashCodes[key]
            if (newHash != null && newHash != oldHash) {
                changed[key] = Pair(oldHash, newHash)
            }
        }
        return changed
    }

    // ==================== NOT IMPLEMENTED METHODS ====================
    // The following methods are intentionally not implemented to enforce
    // the use of secret-based access patterns (findBySecret, deleteBySecret, etc.)
    // which ensure proper decryption, change tracking, and resource cleanup.
    // Methods are organized following CRUD order: Create, Read, (no Update), Delete.

    // ==================== CREATE ====================

    /**
     * # insert
     *
     * This method is not supported and should not be used. Use `save(entity)` instead for entity insertion.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `entity`: The entity to insert.
     *
     * ## Returns
     * - Not implemented.
     */
    override fun <S : T> insert(entity: S): S = throw NotImplementedError("insert(entity) is intentionally not implemented. Use save(entity) instead.")

    /**
     * # insert
     *
     * This method is not supported and should not be used. Use `saveAll(entities)` instead for batch insertion.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `entities`: The entities to insert.
     *
     * ## Returns
     * - Not implemented.
     */
    override fun <S : T> insert(entities: Iterable<S>): List<S> = throw NotImplementedError("insert(entities) is intentionally not implemented. Use saveAll(entities) instead.")

    // ==================== READ ====================

    /**
     * # findById
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     *
     * IDs are derived from secrets and cannot be reversed. You must use the original secret to find entities.
     *
     * ## Parameters
     * - `id`: The CID of the entity (not supported).
     *
     * ## Returns
     * - Always throws NotImplementedError.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented. Use `findBySecret(secret)` instead.
     */
    override fun findById(id: CID): Optional<T> =
        throw NotImplementedError("findById is intentionally not implemented. Use findBySecret(secret) instead.")

    /**
     * # findAll
     *
     * This method is not supported and should not be used.
     * - Use `findBySecret(secret)` for individual entity retrieval, which ensures proper decryption and change tracking for Encryptable entities.
     * - For batch retrieval, implement a custom method that supports decryption and change tracking for each entity.
     * - Calling this method will throw `NotImplementedException`.
     *
     * ## Returns
     * - Not implemented.
     */
    override fun findAll(): List<T> = throw NotImplementedError("findAll is intentionally not implemented. Use findBySecret(secret) for individual retrieval or implement a custom batch method.")

    /**
     * # findAll (with Sort)
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `sort`: Sort order (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
        override fun findAll(sort: Sort): List<T> = throw NotImplementedError("findAll is intentionally not implemented.")

    /**
     * # findAll (with Pageable)
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `pageable`: Pagination information (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
    override fun findAll(pageable: Pageable): Page<T> = throw NotImplementedError("findAll is intentionally not implemented.")

    /**
     * # findAllById
     *
     * This method is not supported and should not be used.
     * - Use `findBySecret(secret)` for individual entity retrieval, which ensures proper decryption and change tracking for Encryptable entities.
     * - For batch retrieval, implement a custom method that supports decryption and change tracking for each entity.
     * - Calling this method will throw `NotImplementedException`.
     *
     * ## Parameters
     * - `ids`: Iterable of CID for entities.
     *
     * ## Returns
     * - Not implemented.
     */
    override fun findAllById(ids: Iterable<CID>): List<T> =
        throw NotImplementedError("findAllById(ids: Iterable<CID?>) is intentionally not implemented. Use findBySecret(secret) for individual retrieval or implement a custom batch method.")

    /**
     * # findOne
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `example`: Query by example criteria (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
    override fun <S : T> findOne(example: Example<S>): Optional<S> = throw NotImplementedError("findOne(example) is intentionally not implemented.")

    /**
     * # findAll (with Example)
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `example`: Query by example criteria (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
    override fun <S : T> findAll(example: Example<S>): List<S> = throw NotImplementedError("findAll(example) is intentionally not implemented.")

    /**
     * # findAll (with Example and Sort)
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `example`: Query by example criteria (not supported).
     * - `sort`: Sort order (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
    override fun <S : T> findAll(example: Example<S>, sort: Sort): List<S> = throw NotImplementedError("findAll(example) is intentionally not implemented.")

    /**
     * # findAll (with Example and Pageable)
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `example`: Query by example criteria (not supported).
     * - `pageable`: Pagination information (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
    override fun <S : T> findAll(example: Example<S>, pageable: Pageable): Page<S> = throw NotImplementedError("findAll(example) is intentionally not implemented.")

    /**
     * # findBy
     *
     * This method is not supported and should not be used.
     * Use `findBySecret(secret)` instead, which ensures proper decryption and change tracking for Encryptable entities.
     * Calling this method will throw `NotImplementedError`.
     *
     * ## Parameters
     * - `example`: Query by example criteria (not supported).
     * - `queryFunction`: Fluent query function (not supported).
     *
     * ## Returns
     * - Not implemented.
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented.
     */
    override fun <S : T, R> findBy(
        example: Example<S>,
        queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>
    ): R = throw NotImplementedError("findBy is not implemented.")

    // ==================== DELETE ====================

    /**
     * # deleteById
     *
     * This method is not supported and should not be used.
     * Use `deleteBySecret(secret)` or `deleteBySecrets(secrets)` instead, which ensures proper decryption and resource cleanup for Encryptable entities.
     *
     * IDs are derived from secrets and cannot be reversed. You must use the original secret to delete entities.
     *
     * ## Parameters
     * - `id`: The CID of the entity (not supported).
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented. Use `deleteBySecret(secret)` instead.
     */
    override fun deleteById(id: CID) =
        throw NotImplementedError("deleteById is intentionally not implemented. Use deleteBySecret(secret) instead.")

    /**
     * # deleteAllById
     *
     * This method is not supported and should not be used.
     * Use `deleteBySecret(secret)` or `deleteBySecrets(secrets)` instead, which ensures proper decryption and resource cleanup for Encryptable entities.
     *
     * IDs are derived from secrets and cannot be reversed. You must use the original secret to delete entities.
     *
     * ## Parameters
     * - `ids`: Iterable of CIDs of the entities (not supported).
     *
     * ## Throws
     * - `NotImplementedError` - This method is intentionally not implemented. Use `deleteBySecrets(secrets)` instead.
     */
    override fun deleteAllById(ids: Iterable<CID>) =
        throw NotImplementedError("deleteAllById is intentionally not implemented. Use deleteBySecrets(secrets) instead.")

    /**
     * # delete
     *
     * Deletes the given entity and all associated GridFS files.
     * This operation is intentionally not implemented.
     *
     * ## Throws
     * - `NotImplementedError` always.
     */
    override fun delete(entity: T) = throw NotImplementedError("delete(entity: T) is intentionally not implemented.")

    /**
     * # deleteAll
     *
     * Deletes all entities in the repository and their associated GridFS files.
     * This operation is intentionally not implemented.
     *
     * ## Throws
     * - `NotImplementedError` always.
     */
    override fun deleteAll() = throw NotImplementedError("deleteAll() is intentionally not implemented.")

    /**
     * # deleteAll
     *
     * Deletes all given entities and their associated GridFS files.
     * This operation is intentionally not implemented.
     *
     * ## Throws
     * - `NotImplementedError` always.
     */
    override fun deleteAll(entities: Iterable<T>) = throw NotImplementedError("deleteAll(entities: Iterable<T>) is intentionally not implemented.")

    /**
     * # EntityInfo
     *
     * Wrapper for entity and its initial hashCode.
     *
     * ## Properties
     * - `entity`: The Encryptable entity instance.
     * - `initialHashCode`: The hash code of the entity at the time it was loaded/tracked.
     */
    private data class EntityInfo<T : Encryptable<T>>(
        val entity: T,
        val initialHashCode: MutableMap<String, Int>
    )
}
