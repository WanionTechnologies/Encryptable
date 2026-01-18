package tech.wanion.encryptable.mongo

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.gridfs.GridFsTemplate
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.MasterSecretHolder
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.mongo.CID.Companion.cid
import tech.wanion.encryptable.mongo.Encryptable.Companion.getSecretOf
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.HKDF
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.Limited.smartReplaceAll
import tech.wanion.encryptable.util.extensions.*

/**
 * # Encryptable
 *
 * Abstract base class for secure, persistent entities supporting field-level encryption, nested object graphs, and large binary fields stored in MongoDB GridFS.
 *
 * ## Features
 * - **Field-level encryption:** Fields annotated with `@Encrypt` are automatically encrypted/decrypted using AES256.
 * - **GridFS integration:** All large ByteArray fields (>1KB) are stored in GridFS, regardless of whether they are annotated with `@Encrypt`. Encryption/decryption of these fields only occurs if they are annotated with `@Encrypt`.
 * - **Automatic lifecycle:** On creation, `prepare()` encrypts fields and sets the ID. On retrieval, `restore(secret)` restores and decrypts fields.
 * - **Automagic file retrieval:** Any get request for a large file field is intercepted by `EncryptableFieldGetAspect` and the file is automagically loaded from GridFS, so you do not need to manually call a load method for these fields.
 * - **Deterministic ID:** ObjectId is generated from a secret, supporting both standard and HKDF-based strategies.
 * - **Change detection:** Change detection is fully automatic for all persisted fields, including child entities. However, if you use custom objects as persisted fields, those custom objects must implement proper hashCode and equals methods to ensure correct change detection.
 * - **Thread safety:** Uses thread-safe caches for reflection metadata and session-only tracking of GridFS ObjectIds.
 * - **List management:** Lists of Encryptable entities are lazy loaded when accessed. Adding an entity to such a list will automatically add it to the database, and removing an entity will also remove it from the database, ensuring consistency between memory and persistent storage.
 *
 * ## Usage
 * - On first saving to MongoDb, automagically calls `prepare()` to encrypt fields and set the ID.
 * - On retrieval, call `restore(secret)`.
 * - For large files, you do not need to manually call a load method; any get request for the field will trigger automagically loading via the aspect.
 * - The secret is always required for encryption/decryption and is never persisted with the entity.
 * - For lists of Encryptable entities, modifications (add/remove) are automatically reflected in the database.
 *
 * ## Implementation Notes
 * - Only `gridFsFields` is persisted for GridFS tracking; all other caches are transient.
 * - Orphaned GridFS files may remain if fields are removed from the class; consider a cleanup strategy if needed.
 * - Not thread-safe for concurrent updates to the same instance.
 * - The hashCode implementation already covers all persisted variables, so there is no need to hash non-persisted variables.
 *
 * ## Limitations
 * - MongoDB BSON document size limit is 16MB. Ensure the total size of the entity (including all encrypted fields and nested objects) does not exceed this limit.
 * - Cycle detection is INTENTIONALLY NOT implemented. Deep or cyclic object graphs may cause stack overflow or infinite recursion.
 *
 * @property secret High-entropy secret for deterministic ID generation and encryption. Used as path parameter and decryption key. Not persisted.
 * @property id MongoDB CID, deterministically generated from the secret. Abstract, must be implemented by subclasses.
 * @property gridFsFields MutableList of field names for ByteArray fields stored in GridFS.
 * @property metadata Cached metadata for encrypted fields and ID strategy.
 * @property encryptableFieldMap Map of field names for fields of type Encryptable to their corresponding secret values.
 * @property encryptableListFieldMap Map of field names for fields of type List<Encryptable> to their corresponding list of secret values.
 * @property gridFsFieldIdMap Map of field names to their corresponding GridFS ObjectIds for fields stored in GridFS.
 */
@Suppress("unused", "UNCHECKED_CAST")
abstract class Encryptable<T: Encryptable<T>> {
    /**
     * Companion Object
     *
     * Utilities for field-level encryption metadata and deterministic ID generation.
     *
     * - Caches field metadata for fast access and reduced reflection overhead.
     * - Supports both standard and HKDF-based ID generation strategies.
     * - Ensures that all field-level encryption logic is centralized and reusable.
     */
    companion object {
        /**
         * encryptableMetadataMap
         *
         * Thread-safe cache for metadata about fields annotated with `@Encrypt` for each Encryptable class.
         * Improves performance by avoiding repeated reflection lookups.
         */
        private val encryptableMetadataMap = ConcurrentHashMap<Class<out Encryptable<*>>, Metadata>()

        /**
         * encryptFieldMetadataMap
         *
         * Thread-safe cache for fields annotated with `@Encrypt` in non-Encryptable classes.
         */
        private val encryptFieldMetadataMap = ConcurrentHashMap<Class<*>, List<Field>>()

        /**
         * gridFsTemplate
         *
         * GridFsTemplate for storing/retrieving large encrypted files in MongoDB GridFS.
         * Used for fields of type ByteArray that exceed 1KB in size.
         */
        private val gridFsTemplate: GridFsTemplate = getBean(GridFsTemplate::class.java)

        /**
         * # of
         *
         * Retrieves an Encryptable entity instance by its secret using the appropriate repository.
         *
         * - Locates the repository for the given Encryptable class.
         * - Attempts to find the entity instance using the provided secret.
         * - Returns `null` if no entity is found for the secret.
         *
         * @param secret The secret string used for deterministic ID generation and encryption.
         * @param encryptableClass The Encryptable class type (optional, inferred from T if not provided).
         * @return The Encryptable entity instance if found, or `null` otherwise.
         */
        inline fun <reified T: Encryptable<T>> of(secret: String, encryptableClass: Class<T> = T::class.java): T? =
            EncryptableContext.getRepositoryForEncryptableClass(encryptableClass).findBySecretOrNull(secret)

        /**
         * # asSecretOf
         *
         * Extension function for String to retrieve an Encryptable entity instance by its secret.
         *
         * - Useful because secrets in this system are always strings.
         * - Provides a clear, type-safe, and idiomatic way to retrieve an entity using a secret string.
         *
         * Example:
         * ```kotlin
         * val entity = "mySecretValue".asSecretOf<MyEntity>()
         * ```
         */
        inline fun <reified T: Encryptable<T>> String.asSecretOf(encryptableClass: Class<T> = T::class.java): T? = of(this, encryptableClass)

        /**
         * getMetadataFor
         *
         * Retrieves cached metadata for the given Encryptable class.
         *
         * - Uses a thread-safe cache to store and reuse reflection metadata for each Encryptable class, improving performance by avoiding repeated reflection lookups.
         * - The cache is populated using `computeIfAbsent`, so metadata is only computed once per class.
         *
         * @param encryptableClass The Encryptable class for which metadata is requested.
         * @return Metadata containing encrypted fields and ID strategy for the class.
         */
        fun getMetadataFor(encryptableClass: Class<out Encryptable<*>>): Metadata =
            encryptableMetadataMap.computeIfAbsent(encryptableClass) { Metadata(it) }

        /**
         * getEncryptFieldsFor
         *
         * Returns a list of fields annotated with `@Encrypt` for the given class.
         *
         * @param clazz The class to inspect.
         * @return List of fields annotated with `@Encrypt`.
         */
        fun getEncryptFieldsFor(clazz: Class<*>): List<Field> =
            encryptFieldMetadataMap.computeIfAbsent(clazz) {
                it.declaredFields.filter { field -> field.isAnnotationPresent(Encrypt::class.java) }
                    .onEach { field -> field.isAccessible = true }
            }

        /**
         * # getSecretOf
         *
         * Returns the secret used for encryption operations.
         *
         * **Behavior varies by ID strategy:**
         * - **@HKDFId entities:** Returns the entity's own secret (set via `withSecret()`)
         * - **@Id entities:** Returns the master secret (shared across all @Id entities)
         *
         * **Use cases:**
         * - Internal framework operations (rotation, relationship management)
         * - Advanced integrations requiring encryption key access
         *
         * **Security:**
         * - Always call `markForWiping()` after use
         * - Never log the actual secret value
         * - Don't cache secrets across requests
         *
         * **Example:**
         * ```kotlin
         * // @HKDFId: returns entity's own secret
         * val userSecret = getSecretOf(user)  // "user-secret-12345..."
         *
         * // @Id: returns master secret (NOT the entity's CID)
         * val deviceSecret = getSecretOf(device)  // MasterSecretHolder.getMasterSecret()
         * ```
         *
         * @param encryptable The Encryptable entity instance
         * @return Encryption secret (@HKDFId: entity's secret, @Id: master secret)
         * @throws IllegalStateException if secret is null or master secret not configured
         * @see getUnsafeSecretOf For nullable secret access
         * @see markForWiping For registering secrets for memory clearing
         */
        fun getSecretOf(encryptable: Encryptable<*>): String {
            val secret = encryptable.secret
            requireNotNull(secret) { "The secret of the provided Encryptable instance is null." }
            return secret
        }

        /**
         * # getUnsafeSecretOf
         *
         * Returns the entity's **own secret** (set via `withSecret()`), allowing null.
         *
         * **Important difference from `getSecretOf()`:**
         * - This method **always** returns the entity's own secret, regardless of entity type
         * - For **@HKDFId entities:** Returns the entity's secret (same as `getSecretOf()`)
         * - For **@Id entities:** Returns the entity's CID/secret (NOT the master secret)
         *
         * Use `getSecretOf()` if you need the encryption secret (which differs for @Id entities).
         *
         * **Warning:** Bypasses null checks. Use only when nullability is explicitly handled.
         *
         * @param encryptable The Encryptable entity instance
         * @return The entity's own secret, or null if not set
         * @see getSecretOf For getting the encryption secret (returns master secret for @Id entities)
         */
        fun getUnsafeSecretOf(encryptable: Encryptable<*>): String? = encryptable.secret
    }

    /**
     * # secret
     *
     * As the name implies, this value is a secret and should be handled with care.
     *
     * - Sensitive value: Used for deterministic CID generation and AES256 encryption/decryption.
     * - Not persisted: Never stored with the entity; must be provided when creating or decrypting.
     * - Restricted access: Not exposed via a public getter to harden access and prevent accidental usage.
     * - Usage: Only accessible through trusted code paths or the companion object's [getSecretOf] method for rare, specific cases (e.g., cryptography, integrations, debugging).
     * - Length:
     *   - @Id: Must be exactly 22 Base64 characters (16 bytes).
     *   - @HKDFId: Must be a high-entropy string (Encryptable enforces a minimum of 32 characters.).
     */
    @Transient
    private var secret: String? = null

    /**
     * Returns this entity instance with the provided secret set.
     *
     * @param secret The secret to associate with this entity instance.
     * @return This entity instance, cast to its actual type.
     */
    fun withSecret(secret: String): T {
        require(this.secret == null) { "The secret is already set for this entity." }
        if (this.id != null) {
            val id = metadata.strategies.getIDFromSecret(secret, this)
            // Ensure the provided secret generates the same ID as the current one.
            require(this.id == id) {
                "The provided secret generated a different ID. Existing ID: ${this.id}, ID from the provided Secret: $id."
            }
        }
        this.secret = secret
        // Register the secret for clearing at request end to limit exposure in memory
        markForWiping(secret)
        return this as T
    }

    /**
     * Returns this entity instance with the provided CID set as both the ID and secret.
     *
     * This method can only be used with entities using the `@Id` strategy.
     *
     * @param cid The CID to associate with this entity instance.
     * @return This entity instance, cast to its actual type.
     * @throws IllegalStateException if the entity does not use the `@Id` strategy or if the secret is already set.
     */
    fun withSecret(cid: CID): T {
        if (metadata.strategies != Metadata.Strategies.ID)
            throw IllegalStateException("withSecret(CID) can only be used with entities using the @Id strategy.")
        require(this.secret == null) { "The secret is already set for this entity." }
        this.secret = cid.toString()
        return this as T
    }

    /**
     * The CID for the entity, deterministically generated from the secret.
     * This property is abstract and must be implemented by subclasses.
     *
     * Note: Subclasses must explicitly annotate their implementation with @Id or @HKDFId for correct behavior.
     *
     * the CID is only needed for two reasons:
     * - Cryptographic Addressing
     * - MongoDB primary key: every document in MongoDB needs a primary key.
     *
     * **NEVER set this value manually, it is set automatically when the entity is prepared for persistence.
     * if you set this value manually, you will break the entity.**
     *
     * example: if this entity was previously persisted, and you set this value to null, at the end of the request,
     * all the associate resources (GridFsFiles, @PartOf entities) will be deleted, because the entity is considered new (id == null).
     */
    abstract var id: CID?

    /**
     * encryptableFieldMap
     *
     * A map of field names for fields of type Encryptable to their corresponding secret values.
     * Used to manage nested Encryptable objects within this entity.
     */
    private var encryptableFieldMap: MutableMap<String, String> = ConcurrentHashMap()

    /**
     * encryptableListFieldMap
     *
     * A map of field names for fields of type List<Encryptable> to their corresponding list of secret values.
     * Used to manage lists of nested Encryptable objects within this entity.
     */
    private var encryptableListFieldMap: MutableMap<String, MutableList<String>> = ConcurrentHashMap()

    /**
     * gridFsFields
     *
     * SynchronizedList of field names that are of type ByteArray and annotated with @Encrypt.
     * Used to optimize serialization/deserialization processes by identifying binary fields.
     */
    private var gridFsFields: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    /**
     * gridFsFieldIdMap
     *
     * Map of field names to their corresponding GridFS ObjectIds for fields stored in GridFS.
     */
    @Transient
    private val gridFsFieldIdMap = ConcurrentHashMap<String, ObjectId>()

    /**
     * # metadata
     *
     * Cached metadata for fields and ID strategy.
     * Used to optimize reflection and encryption/decryption operations.
     */
    @Transient
    val metadata = getMetadataFor(this::class.java)

    /**
     * # init
     *
     * Initialization block - Registers this entity for conditional cleanup.
     *
     * When an entity is instantiated, it automatically registers itself with its repository
     * for potential cleanup at the end of the request lifecycle.
     *
     * **Cleanup is CONDITIONAL** - it only occurs if the entity remains unpersisted by request end:
     * - ✅ If `save()` is called: Entity is persisted normally, cleanup is skipped
     * - ❌ If NOT saved by request end: Entity is cascade-deleted to prevent orphaned resources
     *
     * This pattern ensures that temporary entities created during request processing (e.g., for
     * validation, computation, or conditional logic) don't leave orphaned data in the database
     * (GridFS files, child entities, etc.) if they're ultimately not needed.
     */
    init {
        (metadata.repository as EncryptableMongoRepository<T>).markForCleanup(this as T)
    }

    /**
     * # isNew
     *
     * Indicates whether the entity is new (not yet persisted).
     *
     * @return True if the id is null, indicating the entity has not been saved to the database.
     */
    fun isNew(): Boolean = this@Encryptable.id == null

    /**
     * # prepare
     *
     * Prepares the entire entity before persistence, setting the ID and processing all persisted fields.
     *
     * - Requires a non-null secret and a null id.
     * - The ID is deterministically generated from the secret using the configured strategy.
     * - For fields that are lists of entities, converts regular lists to `EncryptableList` for automatic management.
     * - Processes all persisted fields, encrypting those marked with `@Encrypt` as needed.
     *
     * @throws IllegalStateException if the secret is null or the id is not null.
     */
    private fun prepare() {
        val secret = requireNotNull(this.secret) { "the Secret can't be Null when preparing. ensure that the secret was set eith: entity.withSecret(secret)." }
        // Register the secret for clearing at request end to limit exposure in memory
        markForWiping(secret)
        requireNull(this@Encryptable.id) { "the ID must be Null when preparing." }
        // Set ID first, before processing lists/nested entities that may need to reference this entity
        this.id = metadata.strategies.getIDFromSecret(secret, this)
        this.metadata.encryptableListFields.forEach { (fieldName, field) ->
            val list: List<Encryptable<*>> = this.getField(fieldName)
            if (list is EncryptableList) return@forEach
            if (encryptableListFieldMap.containsKey(fieldName)) return@forEach
            val encryptableList = EncryptableList(fieldName, this, list)
            field.set(this, encryptableList)
        }
        processFields(true)
    }

    /**
     * # restore
     *
     * Restores the entire entity after retrieval from the database, including all persisted fields and those marked with `@Encrypt`.
     *
     * - Sets the secret for this instance and processes all fields, decrypting those marked with `@Encrypt` and restoring all other persisted fields.
     * - Does not load files from GridFS; fields stored in GridFS are only referenced by their ObjectId and will be lazy loaded on demand.
     *
     * @param secret The secret string used for restoration and decryption.
     * @throws IllegalStateException if secret is not null.
     */
    private fun restore(secret: String) {
        withSecret(secret).processFields()
    }

    /**
     * # integrityCheckAndCleanUp
     *
     * Performs an integrity check on all Encryptable references in this entity and cleans up any missing references.
     *
     * - First, migrates any legacy references to ID-only storage.
     * - Next, performs the integrity check to identify missing references.
     * - Finally, cleans up any missing references from the entity.
     *
     * @return Map<Class<out Encryptable<*>>, Set<String>> of missing secrets per type that were cleaned up.
     */
    private fun integrityCheckAndCleanUp(): Map<Class<out Encryptable<*>>, Set<String>> {
        // First, migrate any legacy references to ID-only storage
        migrateReferencesToIdOnly()
        // Next, perform the integrity check
        val missingByType = integrityCheck()
        // Finally, clean up any missing references
        return cleanUpReferences(missingByType)
    }

    /**
     * # migrateReferencesToIdOnly
     *
     * Detects and fixes legacy reference storage in @Id entities that reference @HKDFId entities.
     *
     * **Context**: In versions prior to 1.0.4, @Id entities may have stored the full secret map
     * when referencing @HKDFId entities. This creates a security risk: if the master secret is
     * compromised, the secrets of @HKDFId entities could be exposed.
     *
     * **What it does**:
     * - Scans encryptableFieldMap and encryptableListFieldMap for @HKDFId entity references
     * - If a reference contains the full secret map, extracts the ID and replaces the reference with ID-only storage
     * - Maintains cryptographic isolation by preventing @Id entities from storing @HKDFId secrets
     *
     * **Scope**: Only applies to @Id entities with references to @HKDFId entities.
     * @HKDFId entities are unaffected (they don't use master secret).
     *
     * **Result**: Automatic migration from secret-based to ID-only reference storage on entity load.
     */
    private fun migrateReferencesToIdOnly() {
        if (metadata.isolated)
            return
        // Check single fields
        encryptableFieldMap.entries.forEach { (fieldName, secret) ->
            if (secret.length == 22) return@forEach // Likely an @Id reference, skip check
            val field = metadata.encryptableFields[fieldName] ?: return@forEach
            val type = field.type as Class<out Encryptable<*>>
            val repository = getMetadataFor(type).repository
            val entity = repository.findBySecretOrNull(secret) ?: return@forEach
            field.set(this, entity.id.toString())
        }
        // Check lists
        encryptableListFieldMap.entries.forEach { (fieldName, secretList) ->
            val field = metadata.encryptableListFields[fieldName] ?: return@forEach
            val type = field.typeParameter() as Class<out Encryptable<*>>
            val repository = getMetadataFor(type).repository
            secretList.smartReplaceAll { secret ->
                if (secret.length == 22) return@smartReplaceAll secret // Likely an @Id reference, skip check
                val entity = repository.findBySecretOrNull(secret) ?: return@smartReplaceAll secret
                return@smartReplaceAll entity.id.toString()
            }
        }
    }

    /**
     * # integrityCheck
     *
     * Performs an integrity check on all Encryptable references in this entity.
     *
     * - Gathers all secrets from `encryptableFieldMap` and `encryptableListFieldMap`, grouped by their Encryptable type.
     * - For each type, queries the corresponding repository to identify any secrets that do not exist in the database.
     * - Returns a map of missing secrets grouped by their Encryptable type.
     *
     * @return Map<Class<out Encryptable<*>>, Set<String>> of missing secrets per type.
     */
    private fun integrityCheck(): Map<Class<out Encryptable<*>>, Set<String>> {
        val secretsByType = Collections.synchronizedMap(mutableMapOf<Class<out Encryptable<*>>, MutableSet<String>>())
        // Group secrets from encryptableFieldMap
        encryptableFieldMap.entries.forEach { (fieldName, secret) ->
            val field = metadata.encryptableFields[fieldName] ?: return@forEach
            val type = field.type as Class<out Encryptable<*>>
            secretsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.add(secret)
        }
        // Group secrets from encryptableListFieldMap
        encryptableListFieldMap.entries.forEach { (fieldName, secretList) ->
            val field = metadata.encryptableListFields[fieldName] ?: return@forEach
            val type = field.typeParameter() as Class<out Encryptable<*>>
            secretsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.addAll(secretList)
        }
        // For each type, check missing secrets
        val missingByType = mutableMapOf<Class<out Encryptable<*>>, Set<String>>()
        // Parallelize the check across types for performance
        secretsByType.entries.parallelForEach(false) { (type, secrets) ->
            val repo = getMetadataFor(type).repository
            val missing = repo.filterNonExistingSecrets(secrets)
            if (missing.isNotEmpty()) missingByType[type] = missing
        }
        return missingByType
    }

    /**
     * # cleanUpReferences
     *
     * Cleans up references to missing Encryptable entities in this entity.
     *
     * - For each type of Encryptable, removes entries from `encryptableFieldMap` and `encryptableListFieldMap`
     *   that correspond to the provided missing secrets.
     * - Collects and returns the CIDs of removed references for reporting.
     *
     * @param missingByType Map<Class<out Encryptable<*>>, Set<String>> of missing secrets per type.
     * @return Map<Class<out Encryptable<*>>, Set<String>> of removed CID strings per type.
     */
    private fun cleanUpReferences(missingByType: Map<Class<out Encryptable<*>>, Set<String>>): Map<Class<out Encryptable<*>>, Set<String>> {
        if (missingByType.isEmpty()) return emptyMap()
        val removedCidsByType = Collections.synchronizedMap(mutableMapOf<Class<out Encryptable<*>>, MutableSet<String>>())
        // Remove from encryptableFieldMap using type-specific sets
        encryptableFieldMap.entries.forEach { entry ->
            val field = metadata.encryptableFields[entry.key] ?: return@forEach
            val type = field.type as Class<out Encryptable<*>>
            val missingSet = missingByType[type] ?: return@forEach
            val typeMetadata = getMetadataFor(type)
            if (missingSet.contains(entry.value)) {
                val cidStr = typeMetadata.strategies.getIDFromSecret(entry.value, type).toString()
                removedCidsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.add(cidStr)
                encryptableFieldMap.remove(entry.key)
            }
        }
        // Remove from encryptableListFieldMap using type-specific sets
        encryptableListFieldMap.entries.forEach { (fieldName, secretList) ->
            val field = metadata.encryptableListFields[fieldName] ?: return@forEach
            val type = field.typeParameter() as Class<out Encryptable<*>>
            val missingSet = missingByType[type] ?: return@forEach
            val typeMetadata = getMetadataFor(type)
            val toRemove = secretList.filter { missingSet.contains(it) }
            toRemove.forEach { secret ->
                val cidStr = typeMetadata.strategies.getIDFromSecret(secret, type).toString()
                removedCidsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.add(cidStr)
            }
            secretList.removeAll(missingSet)
        }
        return removedCidsByType
    }

    /**
     * # touch
     *
     * Updates access-related fields (such as lastAccess) to reflect the latest interaction with the entity.
     *
     * This method is called after restore() to record that the entity was accessed or loaded.
     * For example, you can update a lastAccess timestamp or other audit fields here.
     *
     * Fields are automatically tracked and persisted, so to update a field (e.g., lastAccess),
     * - **you only need to set its new value; the persistence layer will handle saving it.**
     *
     * ## Advanced Usage
     *
     * The `touch` method can also be extended to trigger automated security or audit actions. For instance:
     * - Sending notifications or alerts when a user logs in or accesses sensitive data.
     * - Logging access events for compliance or forensic analysis.
     * - Initiating multifactor authentication or other security workflows.
     *
     * By customizing `touch` in your entity subclasses, you can integrate real-time monitoring, messaging, or audit logic directly into the entity lifecycle, ensuring that every access is tracked and actionable for security and compliance purposes.
     *
     * Override this method in subclasses to customize which fields are updated or which actions are triggered.
     */
    open fun touch() {
        // Example: lastAccess = Instant.now()
        // Override in subclasses to update specific fields or trigger security/audit actions
    }

    /**
     * Reads or writes fields, encrypting or decrypting those marked with `@Encrypt`
     *
     * **ByteArray fields:**
     * - Large ByteArray fields (>1KB) are stored in GridFS, regardless of `@Encrypt` annotation.
     * - Encryption/decryption of these fields only occurs if they are annotated with `@Encrypt`.
     *
     * **Supported types:**
     * - `String`, `ByteArray`, and `List<String>` fields.
     * - For ByteArray fields >1KB, stores encrypted data in GridFS and persists only the ObjectId reference in the entity.
     * - For decryption, restores ObjectId reference for large ByteArray fields, or decrypts directly for smaller fields.
     * - Handles nested objects and lists recursively if annotated with `@Encrypt`.
     *
     * **Automagical loading:**
     * - For large ByteArray fields, automagic loading is handled by the `EncryptableFieldGetAspect`—any get request for such a field will trigger automatic loading from GridFS, so manual loading is not required.
     *
     * @param isWrite If `true`, performs a write (encryption); if `false`, performs a read (decryption).
     * @throws IllegalStateException if `secret` is null.
     *
     * ```kotlin
     * // Example usage
     * processFields(true) // Encrypt fields
     * processFields(false) // Decrypt fields
     * ```
     */
    private fun processFields(isWrite: Boolean = false) {
        // Skip processing if not encryptable
        if (!metadata.encryptable)
            return
        val secret = metadata.strategies.secret(this) ?: throw IllegalStateException("the Secret can't be Null on encryption/decryption.")

        metadata.byteArrayFields.entries.forEach { (fieldName, field) ->
            val bytes = field.get(this) as? ByteArray ?: return@forEach
            val bigArray = bytes.size >= EncryptableConfig.gridFsThreshold
            val isEncrypt = field.isAnnotationPresent(Encrypt::class.java)
            val gridFsField = gridFsFields.contains(fieldName)
            if (!bigArray && !gridFsField) {
                val processed = when (isWrite) {
                    true -> if (isEncrypt) AES256.encrypt(secret, this, bytes) else bytes
                    false -> if (isEncrypt) AES256.decrypt(secret, this, bytes) else bytes
                }
                field.set(this, processed)
                return@forEach
            }
            else if (gridFsField) {
                when (isWrite) {
                    true -> {
                        if (!gridFsFieldIdMap.containsKey(fieldName))
                            throw IllegalStateException("Field $fieldName is marked as GridFS but no ObjectId found in map")
                        field.set(this, gridFsFieldIdMap[fieldName]?.toByteArray())
                    }
                    false -> {
                        // On read, just restore the ObjectId reference; actual file loading is lazy via the aspect.
                        if (bytes.size == 12 && !gridFsFieldIdMap.containsKey(fieldName)) {
                            val objectId = ObjectId(bytes)
                            // Store in map for session use.
                            gridFsFieldIdMap[fieldName] = objectId
                        } else if (gridFsFieldIdMap.containsKey(fieldName)) {
                            // ObjectId already in map, nothing to do
                        } else {
                            throw IllegalStateException("Field $fieldName is marked as GridFS but no ObjectId found in map")
                        }
                    }
                }
            }
        }

        // Process Encryptable fields.
        metadata.encryptableFields.entries.forEach { (fieldName, field) ->
            val innerEncryptable = this.getField<Encryptable<*>?>(fieldName)
            // Ensures that inner Encryptable objects added during construction are registered in the map; without this, they would not be persisted.
            if (innerEncryptable != null && !encryptableFieldMap.contains(fieldName)) {
                encryptableFieldMap[fieldName] =
                    if (this.metadata.isolated) innerEncryptable.secret
                        ?: throw IllegalStateException("Inner Encryptable \"$fieldName\" must have its secret set.")
                    else innerEncryptable.id?.toString()
                        ?: throw IllegalStateException("Inner Encryptable \"$fieldName\" must have its id set.")
            }
            // Clear all encryptable fields.
            // they can't be saved as they exist on their own collection.
            field.set(this, null)
        }

        this.encryptableFieldMap.entries.forEach { entry ->
            val value = entry.value
            entry.setValue(
                if (isWrite)
                    AES256.encrypt(secret, this@Encryptable, value)
                else
                    AES256.decrypt(secret, this@Encryptable, value)
            )
        }
        this.encryptableListFieldMap.values.forEach { list ->
            list.smartReplaceAll {
                if (isWrite)
                    AES256.encrypt(secret, this@Encryptable, it)
                else
                    AES256.decrypt(secret, this@Encryptable, it)
            }
        }

        metadata.encryptFields.values.forEach { field ->
            fun processData(data: Any?): Any? {
                return when (data) {
                    is ByteArray -> data // ByteArray fields are handled above.
                    is String -> if (isWrite) AES256.encrypt(secret, this, data)
                                    else AES256.decrypt(secret, this, data)
                    is List<*> -> {
                        when {
                            data.isListOf(String::class.java) -> data.map { processData(it) }
                            else -> {
                                val elemClass = data.firstOrNull()?.javaClass ?: return data
                                if (elemClass.isAnnotationPresent(Encrypt::class.java))
                                    data.map { processData(it) }
                                else data
                            }
                        }
                    }
                    else -> {
                        data?.let {
                            val innerFields = getEncryptFieldsFor(it.javaClass)
                            if (innerFields.isNotEmpty()) {
                                innerFields.forEach { f ->
                                    val fieldValue = f.get(it)
                                    val processed = processData(fieldValue)
                                    f.set(it, processed)
                                }
                            }
                            it
                        }
                    }
                }
            }
            val fieldObj = field.get(this)
            val processed = processData(fieldObj)
            field.set(this, processed)
        }
    }

    /**
     * # beforeRotation
     *
     * Prepares the entity for secret rotation by deleting all GridFS files and clearing sensitive data.
     *
     * - Requires a non-null secret.
     * - Loads all GridFS fields into memory to ensure they are available before deletion.
     * - Deletes all GridFS files associated with this entity.
     * - Clears the GridFS tracking map.
     * - Sets the secret and id to null to prevent further use.
     *
     * @throws IllegalStateException if the secret is null.
     */
    private fun beforeRotation() {
        requireNotNull(this.secret) { "the Secret can't be Null on before rotation." }
        requireNotNull(this.id) { "the id can't be Null on before rotation." }
        // load all the GridFS files into memory
        gridFsFields.forEach { fieldName ->
            loadGridFsField(fieldName)
        }
        // Delete all GridFS files previously associated with this entity;
        // as they will be re-encrypted with the new secret.
        gridFsFieldIdMap.values.forEach { objectId ->
            gridFsTemplate.delete(Query(Criteria.where("_id").`is`(objectId)))
        }
        // Clear secret and id.
        this.secret = null
        this.id = null
        // Clear GridFS tracking maps.
        // this is to make the aspect 'think' this is a new entity on next save.
        gridFsFields.clear()
        gridFsFieldIdMap.clear()
    }

    /**
     * # loadGridFsField
     *
     * Loads a ByteArray field stored in GridFS into memory.
     *
     * - Only applicable for fields marked as GridFS fields.
     * - Retrieves the file from GridFS using the stored ObjectId reference.
     * - Decrypts the data if the field is annotated with `@Encrypt`.
     * - Updates the field with the loaded ByteArray and updates the entity info to avoid detecting as if had changed.
     *
     * @param fieldName The name of the ByteArray field to load from GridFS.
     * @throws IllegalStateException if the field is not found or if decryption fails.
     */
    private fun loadGridFsField(fieldName: String) {
        // Only proceed if this field is marked as a GridFS field
        if (!gridFsFields.contains(fieldName))
            return

        val field = metadata.byteArrayFields[fieldName] ?: throw IllegalStateException("Field $fieldName not found.")

        val bytes = field.get(this) as ByteArray?
        if (bytes == null || bytes.size != 12) return // Not a GridFS reference or was already loaded.

        // Create the objectId from the 12-byte array
        val objectId = ObjectId(bytes)
        // Cache the ObjectId in the map for future referencing
        gridFsFieldIdMap[fieldName] = objectId

        val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)

        // If secret is null, means that this entity was loaded without findBySecret,
        // so we don't have the secret... which means we can't decrypt the field.
        // instead of throwing an exception, we just return.
        // why? at least we populated the gridFsFieldIdMap.
        if (encryptField && this@Encryptable.secret == null)
            return
        // if we got here, either the secret isn't null or the field isn't encrypted.
        // so we continue.
        var inputStream: InputStream? = null
        try {
            val gridFsFile = gridFsTemplate.findOne(Query(Criteria.where("_id").`is`(objectId)))
            inputStream = gridFsTemplate.getResource(gridFsFile).inputStream
            val rawBytes = inputStream.readFastBytes()
            val secret = this.secret ?: if (encryptField) return else String.EMPTY
            val bytes = if (encryptField) AES256.decrypt(secret, this::class.java, rawBytes) else rawBytes
            field.set(this, bytes)
            if (!isNew())
                updateEntityInfo(fieldName to bytes.first4KBChecksum())
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Updates the entity, re-encrypting its fields with the provided secret.
     *
     * - Handles GridFS storage for large ByteArrays and cleans up old files if needed.
     * - Only allowed if the entity has encrypted fields.
     */
    private fun update() {
        processFields(true)
    }

    /**
     * Updates the entity-related metadata for this entity, ensuring consistency between
     * the in-memory state and the persisted secret info. This prevents unnecessary updates
     * by synchronizing secret-dependent fields, such as encrypted data or GridFS references.
     *
     * Should be called after any mutation to secret-dependent fields, including lazy-loaded fields
     * and binary fields (e.g., after loadByteField).
     *
     * This method delegates to the repository to persist the updated entity info.
     */
    private fun updateEntityInfo(newFieldHash: Pair<String, Int>) = (metadata.repository as EncryptableMongoRepository<T>).updateEntityInfo(this as T, newFieldHash)

    /**
     * Cascading delete for this entity and its children according to the "part-of" relationship.
     * **Should be called by the repository when deleting this entity.**
     *
     * Behavior:
     * - Deletes all GridFS files referenced by this entity, regardless of @PartOf annotation.
     * - For each secret in encryptableFieldMap, deletes child entities only if the field is annotated with @PartOf.
     * - For each secret in encryptableListFieldMap, deletes child entities only if the field is annotated with @PartOf (composition/cascade delete).
     *
     * - Intended for use with "part-of" relationships, ensuring child entities are deleted when the parent is deleted, but only for fields explicitly marked as such.
     * - Does not delete entities for fields not annotated with @PartOf, preserving shared references.
     * - Uses parallel processing as it is IO-bound operations (database deletions).
     */
    private fun cascadeDelete() {
        populateGridFsFieldIdMap()
        // GridFs references are stored on field.
        // we can't access the content without the secret, but at least we can delete the files.
        gridFsFieldIdMap.values.parallelForEach { objectId ->
            gridFsTemplate.delete(Query(Criteria.where("_id").`is`(objectId)))
        }
        // if secret is null, means that this entity was loaded without findBySecret,
        // which means all the fields are still encrypted.
        // including the fields that tracks the children entities.
        if (this@Encryptable.secret == null)
            return
        // if we got here, means that the secret isn't null and all the fields are decrypted.
        // Delete all entities referenced in encryptableFieldMap, only if field is annotated with @PartOf
        encryptableFieldMap.parallelForEach { (fieldName, secret) ->
            val field = metadata.encryptableFields[fieldName] ?: return@parallelForEach
            if (!field.isAnnotationPresent(PartOf::class.java)) return@parallelForEach
            val repo = EncryptableContext.getRepositoryForEncryptableClass(field.type as Class<out Encryptable<*>>)
            repo.deleteBySecret(secret)
        }
        // Delete all entities referenced in encryptableListFieldMap, only if field is annotated with @PartOf
        encryptableListFieldMap.parallelForEach { (fieldName, secretList) ->
            val field = metadata.encryptableListFields[fieldName] ?: return@parallelForEach
            if (!field.isAnnotationPresent(PartOf::class.java)) return@parallelForEach
            val elementType = field.typeParameter()
            val repo = EncryptableContext.getRepositoryForEncryptableClass(elementType as Class<out Encryptable<*>>)
            secretList.parallelForEach { secret ->
                repo.deleteBySecret(secret)
            }
        }
        if (this.id != null)
            metadata.repository.removeEntityInfo(this.id!!)
    }

    /**
     * # populateGridFsFieldIdMap
     *
     * Populates the gridFsFieldIdMap with ObjectIds for all ByteArray fields stored in GridFS.
     *
     * - Iterates over all ByteArray fields in the entity.
     * - For each field, checks if it contains a 12-byte array (indicating a GridFS reference).
     * - Converts the byte array to an ObjectId and stores it in the gridFsFieldIdMap.
     */
    private fun populateGridFsFieldIdMap() {
        metadata.byteArrayFields.entries.parallelForEach { (fieldName, field) ->
            val bytes = field.get(this) as ByteArray?
            if (bytes == null || bytes.size != 12) return@parallelForEach // Not a GridFS reference or was already loaded.
            val objectId = ObjectId(bytes)
            gridFsFieldIdMap[fieldName] = objectId
        }
    }

    /**
     * Returns a string representation of this entity.
     *
     * If the entity has a non-null `id`, returns its string value; otherwise, falls back to the default `toString()` implementation.
     *
     * @return The string representation of the entity, typically its MongoDB ObjectId.
     */
    final override fun toString(): String {
        return this@Encryptable.id?.toString() ?: super.toString()
    }

    /**
     * Checks for deep equality between this [Encryptable] instance and another object.
     *
     * This method first checks for referential and class equality, then compares all relevant fields:
     * - [encryptableListFieldMap], [encryptableFieldMap], [gridFsFields]
     * - All fields listed in [Metadata.persistedFields] are compared, including deep comparison for [ByteArray] fields.
     *
     * The comparison is null-safe and handles deep equality for arrays and collections.
     *
     * @param other The object to compare with.
     * @return `true` if all relevant fields are equal, `false` otherwise.
     */
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (other !is Encryptable<*>) return false

        if (this.id != other.id || this.secret != other.secret) return false

        metadata.persistedFields.values.forEach { field ->
            val thisValue = field.get(this)
            val otherValue = field.get(other)
            if (thisValue == null && otherValue == null) return@forEach  // Both null = equal
            if (thisValue == null || otherValue == null) return false  // One null = not equal
            if (thisValue is ByteArray && otherValue is ByteArray) {
                if (!thisValue.contentEquals(otherValue)) return false
            } else {
                if (thisValue != otherValue) return false
            }
        }
        return true
    }

    /**
     * Computes a hash code for this [Encryptable] instance, consistent with [equals].
     *
     * The hash code is calculated using all relevant fields:
     * - All fields listed in [Metadata.persistedFields], using a checksum for [ByteArray] fields for deep hashing.
     *
     * This ensures that two equal objects will have the same hash code, and handles deep hashing for arrays and collections.
     *
     * @return The computed hash code.
     */
    final override fun hashCode(): Int {
        var result: Int = this@Encryptable.id.hashCode()
        metadata.persistedFields.values.forEach { field ->
            val value = field.get(this) ?: return@forEach
            val fieldResult = if (value is ByteArray) value.first4KBChecksum() else value.hashCode()
            result = 31 * result + fieldResult
        }
        return result
    }

    /**
     * Computes the hashCode for every persisted field of this entity and returns a map of field name to hashCode.
     *
     * This method uses [Metadata.persistedFields] to identify all fields that are persisted in the database.
     * For each field, it retrieves the value from this entity and computes its hashCode (using a checksum for ByteArray fields).
     *
     * @return Map of persisted field name to hashCode of its value.
     *
     * Example:
     * ```kotlin
     * val fieldHashes = entity.persistedFieldHashCodes()
     * println(fieldHashes) // {name=123456, age=42, ...}
     * ```
     */
    fun hashCodes(): MutableMap<String, Int> {
        val result = ConcurrentHashMap<String, Int>()
        metadata.persistedFields.forEach { (name, field) ->
            val hash = when (val value = field.get(this)) {
                is ByteArray -> value.first4KBChecksum()
                null -> 0
                else -> value.hashCode()
            }
            result[name] = hash
        }
        return result
    }

    /**
     * Metadata class containing information about encrypted fields and ID generation strategy for an Encryptable class.
     *
     * - Identifies which fields are encrypted.
     * - Determines the ID generation strategy (standard or HKDF).
     * - Makes encrypted fields accessible for reflection-based operations.
     */
    class Metadata (encryptableClass: Class<out Encryptable<*>>) {
        /**
         * The strategy used for deterministic ID generation (ID or HKDFID).
         */
        val strategies: Strategies

        /**
         * Indicates whether this Encryptable class actually uses encryption.
         */
        val encryptable: Boolean

        /**
         * Indicates whether this Encryptable class is cryptographically isolated (uses HKDFID strategy).
         * When true, the entity uses its own secret for encryption instead of the shared master secret.
         */
        val isolated: Boolean

        /**
         * Repository of this Encryptable class.
         */
        val repository = EncryptableContext.getRepositoryForEncryptableClass(encryptableClass)

        /**
         * Map of field names to Field objects for fields annotated with @Encrypt.
         */
        val encryptableFields: Map<String, Field> = encryptableClass.declaredFields.filter {
            Encryptable::class.java.isAssignableFrom(it.type)
        }.onEach { field -> field.isAccessible = true }
            .associateBy { it.name }
            .toMap(LinkedHashMap())

        /** Map of field names to Field objects for fields of type List<Encryptable> (excluding @Transient fields). */
        val encryptableListFields: Map<String, Field> = encryptableClass.allFields.filter {
            !Modifier.isStatic(it.modifiers) &&
            !it.isAnnotationPresent(Transient::class.java) &&
             List::class.java.isAssignableFrom(it.type) &&
             Encryptable::class.java.isAssignableFrom(it.typeParameter())
        }.associateBy { it.name }
            .toMap(LinkedHashMap())

        /** Map of field names to Field objects for fields annotated with @Encrypt (excluding @Transient, Encryptable, and List<Encryptable> fields). */
        val encryptFields: Map<String, Field> = encryptableClass.allFields.filter {
            it.isAnnotationPresent(Encrypt::class.java) &&
            !it.isAnnotationPresent(Transient::class.java) &&
            !encryptableFields.contains(it.name) &&
            !encryptableListFields.contains(it.name)
        }.associateBy { it.name }
            .toMap(LinkedHashMap())

        /** Map of field names to Field objects for ByteArray fields (excluding those annotated with @Transient). */
        val byteArrayFields: Map<String, Field> = encryptableClass.allFields.filter {
            !it.isAnnotationPresent(Transient::class.java) &&
            it.type == ByteArray::class.java
        }.associateBy { it.name }
            .toMap(LinkedHashMap())

        /** Map of field names to Field objects for all persisted fields (excluding id, final/static fields, Transient, Encryptable, and List<Encryptable> fields). */
        val persistedFields: Map<String, Field> = encryptableClass.allFields.filter {
            it.name != "id" &&
            !Modifier.isFinal(it.modifiers) &&
            !Modifier.isStatic(it.modifiers) &&
            !it.isAnnotationPresent(Transient::class.java) &&
            !encryptableFields.contains(it.name) &&
            !encryptableListFields.contains(it.name)
        }.associateBy { it.name }
            .toMap(LinkedHashMap())

        init {
            val idField = encryptableClass.getDeclaredField("id").apply { isAccessible = true }

            this.strategies = when (idField.annotations.firstOrNull()) {
                is Id -> Strategies.ID
                is HKDFId -> Strategies.HKDFID
                else -> throw IllegalStateException("The 'id' field must be annotated with either @Id or @HKDFId in class ${encryptableClass.name}.")
            }

            // An Encryptable is considered cryptographically isolated if it uses the HKDFID strategy.
            this.isolated = strategies == Strategies.HKDFID

            // An Encryptable is considered encryptable if it is a @HKDFId entity,
            // or it is an @Id entity with:
            // - at least one @Encrypt field,
            // - AND the master secret is set.
            // if master secret is not set, the method `masterSecretIsSet()` will throw IllegalStateException.
            this.encryptable =
                this.strategies == Strategies.HKDFID || (encryptFields.isNotEmpty() && MasterSecretHolder.masterSecretIsSet())
        }

        /**
         * # Strategies Enum
         *
         * Defines two strategies for generating CIDs and deriving encryption keys:
         *
         * - **ID (@Id):** Uses secret directly as CID (22 chars). Encrypted fields use master secret (shared across all @Id entities).
         * - **HKDFID (@HKDFId):** Derives CID via HKDF (≥32 chars). Encrypted fields use entity's own secret (per-entity isolation).
         *
         * | Aspect | @Id | @HKDFId |
         * |--------|-----|---------|
         * | **CID Generation** | Direct (secret = CID) | HKDF-derived (one-way) |
         * | **Encryption Keys** | Master secret (shared) | Entity secret (isolated) |
         * | **Secret Rotation** | ❌ Not supported | ✅ Supported (`rotateSecret()`) |
         * | **Master Secret Required** | Yes (if using @Encrypt) | No (independent) |
         * | **Use Case** | Public IDs, shared resources | User accounts, sensitive data |
         */
        enum class Strategies(val id: (String, Class<out Encryptable<*>>) -> CID, val secret: (Encryptable<out Encryptable<*>>) -> String?) {
            /**
             * # ID Strategy (@Id)
             *
             * Uses the secret **directly** as the CID (22-char Base64 URL-safe).
             * Encrypted fields use the **master secret** (shared across all @Id entities).
             *
             * - Secret must be exactly 22 characters (use `CID.random()`)
             * - CID is a **non-secret** (safe to share/expose)
             * - No secret rotation supported (changing secret = changing CID)
             * - Master secret required if using @Encrypt fields
             *
             * **Use for:** Public IDs, shared resources, devices
             */
            ID({ secret, _ -> secret.cid }, { MasterSecretHolder.getMasterSecret() }),

            /**
             * # HKDFID Strategy (@HKDFId)
             *
             * Derives the CID from the secret using **HKDF** (one-way derivation).
             * Encrypted fields use the **entity's own secret** (which means per-entity cryptographic isolation).
             *
             * - Secret must be ≥32 characters (high entropy recommended)
             * - CID is derived, cannot be reversed to obtain secret
             * - Per-entity secret rotation supported via `rotateSecret()`
             * - Master secret is **never used** (fully independent)
             * - Class namespacing: same secret + different class = different CID
             *
             * **Use for:** User accounts, sensitive per-entity data requiring isolation
             */
            HKDFID({ secret, sourceClass ->
                require(secret.length >= 32) { "For HKDFID strategy, the secret must be at least 32 characters long." }
                HKDF.deriveFromEntropy(secret, sourceClass, context = "CID", byteLength = 16).cid
            }, { encryptable -> encryptable.secret });

            /**
             * Returns the CID generated from the secret and source class.
             *
             * This method applies the strategy's ID generation logic to produce a CID.
             *
             * @param secret The secret string used to generate the CID
             * @param sourceClass The Encryptable class (used for namespacing in HKDFID)
             * @return The generated CID
             *
             * @throws IllegalArgumentException if secret doesn't meet strategy requirements
             *         - ID: Must be exactly 22 characters
             *         - HKDFID: Must be at least 32 characters
             *
             * @see CID For the Compact ID type
             */
            fun getIDFromSecret(secret: String, sourceClass: Class<out Encryptable<*>>): CID = id(secret, sourceClass)

            /**
             * Returns the CID generated from the secret and source instance.
             *
             * Convenience overload that extracts the class from the instance.
             *
             * @param secret The secret string used to generate the CID
             * @param source The Encryptable instance (class extracted for namespacing)
             * @return The generated CID
             *
             * @throws IllegalArgumentException if secret doesn't meet strategy requirements
             *
             * @see getIDFromSecret
             */
            fun getIDFromSecret(secret: String, source: Encryptable<*>): CID =
                getIDFromSecret(secret, source::class.java)
        }
    }
}
