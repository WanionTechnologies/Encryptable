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
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import tech.wanion.encryptable.EncryptableContext
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
 * - **Parallel processing:** Encryption/decryption is parallelized for performance.
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
 * @property createdByIP The IP address of the creator of this entity. Set automatically from the current request context.
 * @property createdAt Timestamp of entity creation. Set automatically to the current time upon instantiation.
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
         * Returns the secret of an Encryptable instance.
         *
         * > **Note:** Access is intentionally restricted to avoid accidental exposure; use only in trusted, specific cases.
         *
         * @param encryptable The Encryptable entity instance.
         * @return The secret string for this entity (non-null).
         */
        fun getSecretOf(encryptable: Encryptable<*>): String {
            val secret = encryptable.secret
            requireNotNull(secret) { "The secret of the provided Encryptable instance is null." }
            return secret
        }

        /**
         * # getUnsafeSecretOf
         *
         * Returns the secret of an Encryptable instance, allowing null.
         *
         * > **Warning:** This method bypasses null checks and should be used with extreme caution.
         * Use only in scenarios where nullability is explicitly handled and understood.
         * @param encryptable The Encryptable entity instance.
         * @return The secret string for this entity, or null if not set.
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
            val id = metadata.idStrategy.getIDFromSecret(secret, this)
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
     * createdByIP
     *
     * The IP address of the creator of this entity. Set automatically from the current request context.
     */
    var createdByIP: String = EncryptableContext.getRequestIP()

    /**
     * createdAt
     *
     * Timestamp of entity creation. Set automatically to the current time upon instantiation.
     */
    var createdAt: Instant = Instant.now()

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
    fun isNew(): Boolean = id == null

    /**
     * # prepare
     *
     * Prepares the entire entity before persistence, setting the ID and processing all persisted fields.
     *
     * - Requires a non-null secret and a null id.
     * - The ID is deterministically generated from the secret using the configured strategy.
     * - For fields that are lists of entities, converts regular lists to `EncryptableList` for automatic management.
     * - Processes all persisted fields, encrypting those marked with `@Encrypt` as needed (in parallel if possible).
     *
     * @throws IllegalStateException if the secret is null or the id is not null.
     */
    private fun prepare() {
        val secret = requireNotNull(this.secret) { "the Secret can't be Null when preparing. ensure that the secret was set eith: entity.withSecret(secret)." }
        // Register the secret for clearing at request end to limit exposure in memory
        markForWiping(secret)
        requireNull(id) { "the ID must be Null when preparing." }
        // Set ID first, before processing lists/nested entities that may need to reference this entity
        this.id = metadata.idStrategy.getIDFromSecret(secret, this)
        this.metadata.encryptableListFields.parallelForEach { (fieldName, field) ->
            val list: List<Encryptable<*>> = this.getField(fieldName)
            if (list is EncryptableList) return@parallelForEach
            if (encryptableListFieldMap.containsKey(fieldName)) return@parallelForEach
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
     * Combines the integrity check and cleanup into a single operation.
     * @return Map<Class<out Encryptable<*>>, Set<String>> of removed CID strings per type.
     */
    private fun integrityCheckAndCleanUp(): Map<Class<out Encryptable<*>>, Set<String>> {
        val missingByType = integrityCheck()
        return cleanUpReferences(missingByType)
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
        encryptableFieldMap.entries.parallelForEach { (fieldName, secret) ->
            val field = metadata.encryptableFields[fieldName] ?: return@parallelForEach
            val type = field.type as Class<out Encryptable<*>>
            secretsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.add(secret)
        }
        // Group secrets from encryptableListFieldMap
        encryptableListFieldMap.entries.parallelForEach { (fieldName, secretList) ->
            val field = metadata.encryptableListFields[fieldName] ?: return@parallelForEach
            val type = field.typeParameter() as Class<out Encryptable<*>>
            secretsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.addAll(secretList)
        }
        // For each type, check missing secrets
        val missingByType = mutableMapOf<Class<out Encryptable<*>>, Set<String>>()
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
        encryptableFieldMap.entries.parallelForEach { entry ->
            val field = metadata.encryptableFields[entry.key] ?: return@parallelForEach
            val type = field.type as Class<out Encryptable<*>>
            val missingSet = missingByType[type] ?: return@parallelForEach
            val typeMetadata = getMetadataFor(type)
            if (missingSet.contains(entry.value)) {
                val cidStr = typeMetadata.idStrategy.getIDFromSecret(entry.value, type).toString()
                removedCidsByType.computeIfAbsent(type) { Collections.synchronizedSet(mutableSetOf()) }.add(cidStr)
                encryptableFieldMap.remove(entry.key)
            }
        }
        // Remove from encryptableListFieldMap using type-specific sets
        encryptableListFieldMap.entries.parallelForEach { (fieldName, secretList) ->
            val field = metadata.encryptableListFields[fieldName] ?: return@parallelForEach
            val type = field.typeParameter() as Class<out Encryptable<*>>
            val missingSet = missingByType[type] ?: return@parallelForEach
            val typeMetadata = getMetadataFor(type)
            val toRemove = secretList.filter { missingSet.contains(it) }
            toRemove.forEach { secret ->
                val cidStr = typeMetadata.idStrategy.getIDFromSecret(secret, type).toString()
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
     * - Initiating multi-factor authentication or other security workflows.
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
     * Reads or writes fields, encrypting or decrypting those marked with `@Encrypt` in parallel.
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
        requireNotNull(this.secret) { "the Secret can't be Null on encryption/decryption." }
        val secret = this.secret as String

        metadata.byteArrayFields.entries.parallelForEach { (fieldName, field) ->
            val bytes = field.get(this) as? ByteArray ?: return@parallelForEach
            val bigArray = bytes.size >= EncryptableConfig.gridFsThreshold
            val isEncrypt = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            val gridFsField = gridFsFields.contains(fieldName)
            if (!bigArray && !gridFsField) {
                val processed = when (isWrite) {
                    true -> if (isEncrypt) AES256.encrypt(secret, this, bytes) else bytes
                    false -> if (isEncrypt) AES256.decrypt(secret, this, bytes) else bytes
                }
                field.set(this, processed)
                return@parallelForEach
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
        metadata.encryptableFields.entries.parallelForEach { (fieldName, field) ->
            val innerEncryptable = this.getField<Encryptable<*>?>(fieldName)
            // Ensures that inner Encryptable objects added during construction are registered in the map; without this, they would not be persisted.
            if (innerEncryptable != null && !encryptableFieldMap.contains(fieldName))
                encryptableFieldMap[fieldName] = innerEncryptable.secret ?: throw IllegalStateException("The secret of field '$fieldName' can't be null.")
            // Clear all encryptable fields.
            // they can't be saved as they exist on their own collection.
            field.set(this, null)
        }

        this.encryptableFieldMap.entries.parallelForEach { entry ->
            val value = entry.value
            entry.setValue(
                if (isWrite)
                    AES256.encrypt(secret, this@Encryptable, value)
                else
                    AES256.decrypt(secret, this@Encryptable, value)
            )
        }
        this.encryptableListFieldMap.values.parallelForEach { list ->
            list.smartReplaceAll {
                if (isWrite)
                    AES256.encrypt(secret, this@Encryptable, it)
                else
                    AES256.decrypt(secret, this@Encryptable, it)
            }
        }

        metadata.encryptFields.values.parallelForEach { field ->
            fun processData(data: Any?): Any? {
                return when (data) {
                    is ByteArray -> return data // ByteArray fields are handled above.
                    is String -> if (isWrite) AES256.encrypt(secret, this, data)
                                    else AES256.decrypt(secret, this, data)
                    is List<*> -> {
                        when {
                            data.isListOf(String::class.java) -> data.map { processData(it) }
                            else -> {
                                val elemClass = data.firstOrNull()?.javaClass ?: return data
                                if (metadata.encryptable && elemClass.isAnnotationPresent(Encrypt::class.java))
                                    data.map { processData(it) }
                                else data
                            }
                        }
                    }
                    else -> {
                        data?.let {
                            if (!metadata.encryptable) return it
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
        gridFsFields.parallelForEach { fieldName ->
            loadGridFsField(fieldName)
        }
        // Delete all GridFS files previously associated with this entity;
        // as they will be re-encrypted with the new secret.
        gridFsFieldIdMap.values.parallelForEach { objectId ->
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
        // Cache the ObjectId in the map for future reference
        gridFsFieldIdMap[fieldName] = objectId

        val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)

        // If secret is null, means that this entity was loaded without findBySecret,
        // so we don't have the secret... which means we can't decrypt the field.
        // instead of throwing an exception, we just return.
        // why? at least we populated the gridFsFieldIdMap.
        if (encryptField && secret == null)
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
     * - Uses parallel processing for efficiency.
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
        if (secret == null)
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
        return id?.toString() ?: super.toString()
    }

    /**
     * Checks for deep equality between this [Encryptable] instance and another object.
     *
     * This method first checks for referential and class equality, then compares all relevant fields:
     * - [encryptableListFieldMap], [encryptableFieldMap], [gridFsFields], [createdByIP], [createdAt]
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
        var result: Int = id.hashCode()
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
        metadata.persistedFields.parallelForEach { (name, field) ->
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
        val idStrategy: IDStrategy

        /**
         * Indicates whether the class has any fields annotated with @Encrypt.
         */
        val encryptable: Boolean

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

            this.idStrategy = when (idField.annotations.firstOrNull()) {
                is Id -> IDStrategy.ID
                is HKDFId -> IDStrategy.HKDFID
                else -> throw IllegalStateException("The 'id' field must be annotated with either @Id or @HKDFId in class ${encryptableClass.name}.")
            }

            /**
             * On IDStrategy.ID, no fields can be encrypted since the secret is the ID itself, making it a non-secret.
             */
            this.encryptable = idStrategy != IDStrategy.ID

            /**
             * Validation: If the strategy is ID, ensure no fields are annotated with @Encrypt.
             * Fail-fast to prevent false sense of security.
             */
            if (!this.encryptable && encryptFields.isNotEmpty())
                throw IllegalStateException("Encryptable class \"${encryptableClass.name}\" has fields annotated with @Encrypt, but the ID strategy is ID which does not support encryption.")
        }

        /**
         * **Supported strategies for generating deterministic `CID` from a secret.**
         *
         * - `ID`: Uses the secret directly as the `CID`.
         * - `HKDFID`: Uses HKDF to derive the `CID` from the secret.
         *
         * ```kotlin
         * enum class IDStrategy {
         *     ID, HKDFID
         * }
         * ```
         */
        enum class IDStrategy(val strategy: (String, Class<out Encryptable<*>>) -> CID) {
            /**
             * **Standard ID strategy**
             *
             * Uses the secret directly, must be a 22 character base64 url-safe and no padding `String`.
             * Essentially making the secret a non-secret.
             *
             * **TIP:** To generate a valid random CID for this strategy, use:
             * ```kotlin
             * val randomCid = CID.random.toString()
             * ```
             * This produces a URL-safe, 22-character Base64 string suitable for use as a direct ID.
             */
            ID({ secret, _ -> secret.cid }),

            /**
             * **HKDF-based ID strategy**
             *
             * Derives the ID from the secret using HKDF.
             * The secret can be any string of at least 32 characters.
             * Ideally a high entropy string.
             *
             * **Limitation:**
             * Minimum entropy guarantee cannot be strictly enforced for secrets derived from user details (e.g., username, password, 2FA secret).
             * If the secret is deterministically derived from user input, it may have low entropy and could be rejected by entropy validation,
             * even if it is valid for authentication. There is no way to guarantee that all user-derived secrets will always yield high-entropy results.
             *
             * **TIP:** To minimize this limitation, derive at least 64 characters from user details before using as a secret.
             *
             * **TIP²:** For HKDF-based IDs, generate a high-entropy secret using:
             * ```kotlin
             * val secret = String.randomSecret()
             * ```
             * This extension function creates a cryptographically secure, random string of the desired length (e.g., 43+ characters), ideal for use as a secret in HKDF-based ID derivation.
             */
            HKDFID({ secret, sourceClass ->
                require(secret.length >= 32) { "For HKDFID strategy, the secret must be at least 32 characters long." }
                HKDF.deriveFromEntropy(secret, sourceClass, context = "CID", byteLength = 16).cid
            });

            /**
             * Returns the `CID` generated from the secret and source class.
             *
             * @param secret The secret string.
             * @param sourceClass The `Encryptable` class.
             * @return The generated `CID`.
             *
             * ```kotlin
             * val id = IDStrategy.ID.getIDFromSecret(secret, MyEncryptable::class.java)
             * ```
             */
            fun getIDFromSecret(secret: String, sourceClass: Class<out Encryptable<*>>): CID = strategy(secret, sourceClass)

            /**
             * Returns the `CID` generated from the secret and source instance.
             *
             * @param secret The secret string.
             * @param source The `Encryptable` instance.
             * @return The generated `CID`.
             *
             * ```kotlin
             * val id = IDStrategy.HKDFID.getIDFromSecret(secret, myEncryptable)
             * ```
             */
            fun getIDFromSecret(secret: String, source: Encryptable<*>): CID =
                getIDFromSecret(secret, source::class.java)
        }
    }
}
