package tech.wanion.encryptable

import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.storage.IStorage
import tech.wanion.encryptable.storage.Sliced
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.SecurityUtils
import tech.wanion.encryptable.util.extensions.copy
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.markForWiping
import tech.wanion.encryptable.util.extensions.metadata
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Holds the master secret used for encryption and decryption.
 * The master secret is loaded from the application environment or configuration.
 *
 * The master secret can be updated dynamically at any time.
 *
 * **Important:** Changing the master secret does NOT automatically re-encrypt existing data.
 * Data encrypted with the old secret will remain encrypted with that secret.
 * To re-encrypt existing data with a new secret, a secret rotation is required.
 *
 * Usage:
 * - To set or update the master secret:
 *   MasterSecretHolder.setMasterSecret("your-master-secret")
 *
 * - To retrieve the master secret:
 *   val secret = MasterSecretHolder.getMasterSecret()
 *
 * **REMEMBER:** Keep the master secret secure and do not expose it in logs or error messages.
 *
 * The master secret is used only for entities whose `id` is annotated with `@Id`.
 * Entities with `@HKDFId`-annotated ids derive their own unique keys and do not use the master secret at all; they are completely independent of it.
 *
 * ## Master Secret Requirements
 *
 * The master secret must satisfy two requirements:
 * 1. **Length:** At least 74 characters to guarantee 256 bits of entropy (at 3.5 bits per character minimum).
 * 2. **Entropy:** Minimum Shannon entropy of ≥3.5 bits per character, validated via `SecurityUtils.hasMinimumEntropy()`.
 *    This ensures the secret has sufficient randomness to resist brute-force attacks.
 *
 * **Rationale:** A 74-character secret with ≥3.5 bits/char entropy provides approximately 259 bits of entropy,
 * exceeding the 256 bits required for AES-256 key derivation via HKDF-SHA256.
 */
object MasterSecretHolder {
    /** Logger instance for audit logging. */
    private val logger = LoggerFactory.getLogger(MasterSecretHolder::class.java)

    /** Master secret used for encryption and decryption. Volatile for JVM memory visibility across threads. */
    @Volatile
    private var masterSecret: String? = null

    init {
        val environment = getBean(Environment::class.java)

        // Load the master secret from environment variable or configuration
        val masterSecretFromEnv = environment.getProperty("encryptable.master.secret")

        // validate and set the master secret if it is provided in the environment or configuration.
        if (masterSecretFromEnv != null)
            setMasterSecret(masterSecretFromEnv)
    }

    /**
     * Sets the master secret. If a master secret is already set, it will be updated.
     * @param secret The master secret to set.
     */
    fun setMasterSecret(secret: String?) {
        if (masterSecret != null)
            logger.warn("Master secret is being updated.")
        secret as String
        val secretLength = secret.length
        require(secretLength > 73) { "Master Secret must be at least 74 characters to guarantee 256-bit entropy (got $secretLength characters)." }
        require(SecurityUtils.hasMinimumEntropy(secret)) { "Master Secret has insufficient entropy" }

        // Store an isolated copy — the caller may have marked `secret` for wiping, which would
        // zerify the stored value at request end (same bug as the rotation path, fixed here too).
        masterSecret = secret.copy()
        logger.info("Master secret has been set and is ready for use.")
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
    private fun throwMasterSecretNotSet(): Nothing = throw IllegalStateException("Master Secret is not set.")

    /**
     * Rotates the master secret, re-encrypting all `@Encrypt` fields on all `@Id` (non-isolated) entities
     * from [oldMasterSecret] to [newMasterSecret].
     *
     * ## What This Method Does
     *
     * 1. Validates that [oldMasterSecret] matches the currently set master secret.
     * 2. Validates that [newMasterSecret] meets the length and entropy requirements.
     * 3. Iterates **all** `@Id` (non-isolated) entity collections that have `@Encrypt` fields.
     * 4. For each document:
     *    - Re-encrypts all `@Encrypt` String and List<String> fields (decrypt with old, encrypt with new).
     *    - Re-encrypts all `@Encrypt` ByteArray fields — both inline and storage-backed (including `@Sliced`).
     *    - Re-encrypts inner `@Encrypt` fields of nested objects.
     * 5. Sets the new master secret.
     *
     * ## Safety
     *
     * - **Failure detection via identity check:** Encryptable's `AES256` class silently fails decryption
     *   (returns the input unchanged on authentication failure). This allows safe idempotent retry:
     *   if a document was already rotated, decryption with the old secret will fail and the
     *   identity check (`===` for ByteArray, `!=` for String) will detect it and skip re-encryption.
     * - Storage-backed fields follow an **atomic replace** pattern: new data is written before old data is deleted.
     * - `@HKDFId` (isolated) entities are completely skipped — they do not use the master secret.
     * - `encryptableFieldMap` / `encryptableListFieldMap` values are NOT encrypted for `@Id` entities
     *   (they are plaintext child IDs), so they require no rotation.
     *
     * ## Important
     *
     * - This operation should be performed during a **maintenance window** — concurrent writes to
     *   `@Id` entities during rotation could result in data encrypted with either the old or new secret.
     * - For very large datasets, storage-backed fields will temporarily consume double storage
     *   (new slices are created before old ones are deleted).
     * - If rotation fails partway through, some documents may already be re-encrypted with the new
     *   secret while others remain with the old secret. In that case, the master secret is NOT updated,
     *   and the operation must be retried. Documents already rotated will pass through safely (decryption
     *   with the old secret fails → identity check → skipped).
     *
     * @param oldMasterSecret The current master secret. Must match the currently set value.
     * @param newMasterSecret The new master secret to rotate to. Must meet length (≥74 chars) and entropy requirements.
     * @throws IllegalStateException if no master secret is currently set.
     * @throws IllegalArgumentException if [oldMasterSecret] does not match the current secret,
     *         or if [newMasterSecret] fails validation.
     * @throws Exception if rotation fails for any repository — propagated to prevent setting the new secret on partial failure.
     */
    fun rotateMasterSecret(oldMasterSecret: String, newMasterSecret: String) {
        // Validate old secret matches current
        require(masterSecret != null) { "No master secret is currently set. Cannot rotate." }
        val matches = MessageDigest.isEqual(
            masterSecret!!.toByteArray(), oldMasterSecret.toByteArray()
        )
        require(matches) { "Old master secret does not match the current master secret." }

        // Validate new secret
        val newLength = newMasterSecret.length
        require(newLength > 73) { "New Master Secret must be at least 74 characters to guarantee 256-bit entropy (got $newLength characters)." }
        require(SecurityUtils.hasMinimumEntropy(newMasterSecret)) { "New Master Secret has insufficient entropy." }

        logger.info("Master Secret rotation initiated.")

        // Mark old and new master secrets for wiping after use to minimize time they exist in memory in plaintext form.
        markForWiping(oldMasterSecret, newMasterSecret)

        val repositories = EncryptableContext.getAllRepositories()
        val storageHandler = getBean(StorageHandler::class.java)

        repositories.parallelForEach { repository ->
            val entityClass = repository.getTypeClass()
            val metadata = Encryptable.getMetadataFor(entityClass)

            // Only @Id (non-isolated) entities use the master secret.
            if (metadata.isolated) return@parallelForEach
            // Only entities with @Encrypt fields need rotation.
            if (!metadata.encryptable) return@parallelForEach

            val collection: MongoCollection<Document> = repository.getMongoCollection()
            val cursor = collection.find().iterator()

            try {
                while (cursor.hasNext()) {
                    val doc = cursor.next()
                    var modified = false

                    // Storage deletions are deferred until after the MongoDB document is successfully updated.
                    // This prevents data loss: if replaceOne() fails, old storage objects are still intact.
                    val pendingDeletions = mutableListOf<() -> Unit>()

                    // --- @Encrypt fields (String, List<String>, nested objects) ---
                    // encryptFields excludes ByteArray and Encryptable/List<Encryptable> fields.
                    metadata.encryptFields.forEach { (fieldName, field) ->
                        val value = doc[fieldName] ?: return@forEach
                        val rotated = rotateValue(value, field.type, oldMasterSecret, newMasterSecret, entityClass)
                        if (rotated != null) {
                            doc[fieldName] = rotated
                            modified = true
                        }
                    }

                    // --- @Encrypt ByteArray fields (inline and storage-backed, including @Sliced) ---
                    metadata.byteArrayFields.forEach { (fieldName, field) ->
                        if (!field.isAnnotationPresent(Encrypt::class.java)) return@forEach

                        @Suppress("UNCHECKED_CAST")
                        val storageFieldsList = doc["storageFields"] as? List<String>
                        val isStorageField = storageFieldsList?.contains(fieldName) == true

                        val binary = doc[fieldName] as? Binary ?: return@forEach
                        val referenceOrData = binary.data

                        if (referenceOrData.isEmpty()) return@forEach // Skip empty fields.

                        if (isStorageField) {
                            val storage = storageHandler.getStorageForField(field)
                            val newBytes = if (field.isAnnotationPresent(Sliced::class.java)) {
                                rotateSlicedStorageField(field, storage, referenceOrData, oldMasterSecret, newMasterSecret, entityClass, pendingDeletions)
                            } else {
                                rotateSingleStorageField(field, storage, referenceOrData, oldMasterSecret, newMasterSecret, entityClass, pendingDeletions)
                            }
                            if (newBytes != null) {
                                doc[fieldName] = Binary(newBytes)
                                modified = true
                            }
                        } else {
                            // Inline ByteArray — stored directly in the document.
                            val decrypted = AES256.decrypt(oldMasterSecret, entityClass, referenceOrData)
                            if (decrypted !== referenceOrData) { // identity check: decrypt returns same ref on failure
                                doc[fieldName] = Binary(AES256.encrypt(newMasterSecret, entityClass, decrypted))
                                modified = true
                            }
                        }
                    }

                    if (modified) {
                        collection.replaceOne(Document("_id", doc["_id"]), doc)
                        // MongoDB document is now safely updated — delete old storage objects.
                        // If replaceOne() had thrown above, pendingDeletions would never run,
                        // preserving old storage data and allowing safe retry.
                        pendingDeletions.parallelForEach { it() }
                        logger.info("Rotated Master secret for document _id=${doc["_id"]} in '${collection.namespace.collectionName}'")
                    }
                }
            } catch (e: Exception) {
                logger.error("Master secret rotation failed for entity '${entityClass.name}': ${e.message}", e)
                throw e // Propagate to prevent setting new secret on partial failure
            } finally {
                cursor.close()
            }
        }

        // All documents rotated successfully — update the master secret.
        // A copy is made before assigning because newMasterSecret was already marked for wiping above.
        // Assigning the same String object would cause masterSecret to be zerified at request end,
        // silently destroying the just-rotated secret and breaking all subsequent operations.
        masterSecret = newMasterSecret.copy()
        logger.info("Master Secret rotation completed successfully. Master secret has been updated.")
    }

    /**
     * Recursively rotates a single value from an `@Encrypt` field in a BSON document.
     *
     * Handles:
     * - **String:** Decrypts with old secret, re-encrypts with new secret.
     * - **List:** Maps each String element through rotation.
     * - **Document (nested object):** Recursively rotates inner `@Encrypt` fields.
     *
     * @return The rotated value, or `null` if no rotation was needed (value unchanged).
     */
    private fun rotateValue(
        value: Any,
        fieldType: Class<*>,
        oldSecret: String,
        newSecret: String,
        entityClass: Class<out Encryptable<*>>
    ): Any? {
        return when (value) {
            is String -> {
                val decrypted = AES256.decrypt(oldSecret, entityClass, value)
                if (decrypted != value) AES256.encrypt(newSecret, entityClass, decrypted) else null
            }
            is List<*> -> {
                var changed = false
                val rotated = value.map { item ->
                    if (item is String) {
                        val decrypted = AES256.decrypt(oldSecret, entityClass, item)
                        if (decrypted != item) {
                            changed = true
                            AES256.encrypt(newSecret, entityClass, decrypted)
                        } else item
                    } else item
                }
                if (changed) rotated else null
            }
            is Document -> {
                // Nested object: process inner @Encrypt fields using the Java class metadata.
                val innerFields = Encryptable.getEncryptFieldsFor(fieldType)
                var changed = false
                innerFields.forEach { innerField ->
                    val innerValue = value[innerField.name] ?: return@forEach
                    val rotated = rotateValue(innerValue, innerField.type, oldSecret, newSecret, entityClass)
                    if (rotated != null) {
                        value[innerField.name] = rotated
                        changed = true
                    }
                }
                if (changed) value else null
            }
            else -> null
        }
    }

    /**
     * Rotates a single (non-sliced) storage-backed `@Encrypt` ByteArray field.
     *
     * Pattern: create new → queue old for deletion (deletion happens only after MongoDB is updated).
     *
     * @param pendingDeletions Accumulates deletion lambdas to run after the MongoDB document is safely updated.
     * @return The new reference bytes to store in the document, or `null` if rotation was not needed.
     */
    private fun rotateSingleStorageField(
        field: Field,
        storage: IStorage<Any>,
        referenceBytes: ByteArray,
        oldSecret: String,
        newSecret: String,
        entityClass: Class<out Encryptable<*>>,
        pendingDeletions: MutableList<() -> Unit>
    ): ByteArray? {
        val reference = storage.createReference(referenceBytes) ?: return null
        val rawBytes = storage.read(field.metadata, reference) ?: return null
        val decrypted = AES256.decrypt(oldSecret, entityClass, rawBytes)
        // Identity check: AES256.decrypt returns the same reference on failure.
        if (decrypted === rawBytes) return null

        val reEncrypted = AES256.encrypt(newSecret, entityClass, decrypted)
        // Create new entry first — ensures data exists before old is deleted.
        val newReferenceBytes = storage.createWithBytesReference(field.metadata, reEncrypted)
        // Queue old entry for deletion — executed only after replaceOne() succeeds.
        // If the MongoDB write fails, old storage data remains intact for safe retry.
        pendingDeletions.add { storage.delete(field.metadata, reference) }
        return newReferenceBytes
    }

    /**
     * Rotates a `@Sliced` storage-backed `@Encrypt` ByteArray field.
     *
     * Each slice is independently decrypted with the old secret and re-encrypted with the new secret.
     * New slices are created first, then old slices are queued for deletion (executed only after the MongoDB
     * document is safely updated). This prevents data loss if the MongoDB write fails.
     *
     * Reference layout: `[8-byte originalLength][N × refLen-byte slice references]`
     *
     * @param pendingDeletions Accumulates deletion lambdas to run after the MongoDB document is safely updated.
     * @return The new concatenated reference bytes, or `null` if rotation was not needed.
     */
    private fun rotateSlicedStorageField(
        field: Field,
        storage: IStorage<Any>,
        referenceBytes: ByteArray,
        oldSecret: String,
        newSecret: String,
        entityClass: Class<out Encryptable<*>>,
        pendingDeletions: MutableList<() -> Unit>
    ): ByteArray? {
        val refLen = storage.referenceLength
        if (referenceBytes.size < 8 + refLen) return null // Invalid if it doesn't contain one slice reference.
        val refsBytes = referenceBytes.size - 8
        if (refsBytes % refLen != 0) return null // Invalid.

        val originalLength = ByteBuffer.wrap(referenceBytes, 0, 8).getLong()
        val sliceCount = refsBytes / refLen

        // First pass: decrypt each slice with old secret, re-encrypt with new, store new slices.
        // Old references are collected for deletion after all new slices are safely stored.
        val oldReferences = mutableListOf<Any>()
        val newSliceRefBytes = mutableListOf<ByteArray>()

        for (i in 0 until sliceCount) {
            val sliceRefBytes = referenceBytes.copyOfRange(8 + i * refLen, 8 + (i + 1) * refLen)
            val oldReference = storage.createReference(sliceRefBytes)
                ?: throw IllegalStateException("Invalid reference for slice $i of field '${field.name}' during master secret rotation.")
            oldReferences.add(oldReference)

            val rawBytes = storage.read(field.metadata, oldReference)
                ?: throw IllegalStateException("No data for slice $i of field '${field.name}' during master secret rotation.")

            val decrypted = AES256.decrypt(oldSecret, entityClass, rawBytes)
            if (decrypted === rawBytes)
                throw IllegalStateException("Decryption failed for slice $i of field '${field.name}' during master secret rotation.")

            val reEncrypted = AES256.encrypt(newSecret, entityClass, decrypted)
            newSliceRefBytes.add(storage.createWithBytesReference(field.metadata, reEncrypted))
        }

        // Build new concatenated reference: 8-byte length header + N × refLen references.
        val result = ByteArray(8 + refLen * sliceCount)
        ByteBuffer.wrap(result, 0, 8).putLong(originalLength)
        newSliceRefBytes.forEachIndexed { i, refBytes ->
            refBytes.copyInto(result, destinationOffset = 8 + i * refLen)
        }

        // Second pass: queue old slices for deletion — executed only after replaceOne() succeeds.
        // If the MongoDB write fails, old storage data remains intact for safe retry.
        oldReferences.forEach { ref ->
            pendingDeletions.add { storage.delete(field.metadata, ref) }
        }

        return result
    }
}