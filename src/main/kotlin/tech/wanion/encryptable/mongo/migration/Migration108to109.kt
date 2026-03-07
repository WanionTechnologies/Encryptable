package tech.wanion.encryptable.mongo.migration

import com.mongodb.client.MongoCollection
import org.bson.Document
import org.slf4j.LoggerFactory
import tech.wanion.encryptable.MasterSecretHolder
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.decode64

/**
 * # Migration108to109
 *
 * Migration from 1.0.8 to 1.0.9.
 *
 * ## Background
 *
 * In 1.0.0–1.0.8, `processFields` contained a code path that encrypted ALL values in
 * `encryptableListFieldMap` and `encryptableFieldMap` using the master secret (for `@Id`
 * entities) or entity secret (for `@HKDFId` entities) **without** checking whether the parent
 * entity was isolated.
 *
 * For **`@Id` (non-isolated)** entities, the values stored in `encryptableListFieldMap` and
 * `encryptableFieldMap` should be **plaintext child IDs** — they must never be encrypted.
 * However, if the `@Id` parent entity also had at least one `@Encrypt` field
 * (`metadata.encryptable == true`), the old code would encrypt those plaintext IDs with the
 * master secret before persisting them.
 *
 * `@Id` entities that had **no** `@Encrypt` fields (`metadata.encryptable == false`) were
 * unaffected — the early-return guard `if (!metadata.encryptable) return` prevented the
 * encrypt block from running, so their values remained in plaintext. The `EncryptableList`
 * safeguard ensured the plaintext ID was always placed in the map first, but for entities with
 * `@Encrypt` fields the subsequent `processFields` pass would encrypt it.
 *
 * ## What This Migration Does
 *
 * For every `@Id` entity class that has both `encryptableListFields`/`encryptableFields` **and**
 * at least one `@Encrypt` field (`metadata.encryptable == true`), this migration:
 *
 * 1. Scans every document in the entity's collection.
 * 2. For each entry in `encryptableListFieldMap`, attempts to decrypt the stored value with
 *    the master secret.
 * 3. If decryption succeeds (the result differs from the input — i.e., GCM tag verified), the
 *    plaintext ID is written back in place of the ciphertext.
 * 4. Applies the same logic to every entry in `encryptableFieldMap`.
 * 5. Replaces the document in MongoDB only when at least one field was changed.
 *
 * ## Safety
 *
 * AES-256-GCM's authentication tag guarantees that a decryption attempt with the wrong key
 * (or on already-plaintext data) will fail — `AES256.decrypt` returns the input bytes
 * unchanged on any failure. The migration therefore only modifies values that were genuinely
 * encrypted with the master secret; plaintext IDs (from already-correct documents) are left
 * untouched.
 *
 * ## Note on `@HKDFId` Entities
 *
 * `@HKDFId` (isolated) entities are fully excluded. Their `encryptableListFieldMap` values are
 * child *secrets* encrypted with the parent's own secret — this is correct behaviour and must
 * not be touched.
 */
class Migration108to109 : Migration {

    private val logger = LoggerFactory.getLogger(Migration108to109::class.java)

    override fun fromVersion(): String = "1.0.8"
    override fun toVersion(): String = "1.0.9"

    /**
     * No schema changes are required for 1.0.9; the document structure is identical.
     */
    override fun migrateSchema() {
        // No schema changes needed.
    }

    /**
     * Scans all `@Id` entity collections that have both nested-Encryptable fields and at least
     * one `@Encrypt` field, then decrypts any `encryptableListFieldMap` / `encryptableFieldMap`
     * values that were incorrectly encrypted with the master secret in earlier versions.
     */
    override fun migrateData() {
        val repositories = EncryptableContext.getAllRepositories()

        repositories.parallelForEach { repository ->
            val entityClass = repository.getTypeClass()
            val metadata = Encryptable.getMetadataFor(entityClass)

            // Only @Id (non-isolated) entities are affected.
            if (metadata.isolated) return@parallelForEach

            // Only entities that have @Encrypt fields were affected by the bug.
            // (entities without @Encrypt fields returned early before the encrypt block ran.)
            if (!metadata.encryptable) return@parallelForEach


            val affectedListFields = metadata.encryptableListFields
            val affectedEncryptableFields = metadata.encryptableFields

            // If there are no affected fields at all, skip this entity class entirely.
            if (affectedListFields.isEmpty() && affectedEncryptableFields.isEmpty())
                return@parallelForEach

            val masterSecret = MasterSecretHolder.getMasterSecret()

            val collection: MongoCollection<Document> = repository.getMongoCollection()
            val cursor = collection.find().iterator()
            try {
                while (cursor.hasNext()) {
                    val doc = cursor.next()
                    var modified = false

                    // --- encryptableListFieldMap ---
                    if (affectedListFields.isNotEmpty()) {
                        val listMapDoc = doc["encryptableListFieldMap"] as? Document
                        if (listMapDoc != null) {
                            for (fieldName in affectedListFields.keys) {
                                @Suppress("UNCHECKED_CAST")
                                val entries = listMapDoc[fieldName] as? List<String> ?: continue
                                val decrypted = entries.map { encryptedValue ->
                                    tryDecryptId(masterSecret, entityClass, encryptedValue)
                                }
                                // Only update if at least one value changed.
                                if (decrypted != entries) {
                                    listMapDoc[fieldName] = decrypted
                                    modified = true
                                }
                            }
                            if (modified) {
                                doc["encryptableListFieldMap"] = listMapDoc
                            }
                        }
                    }

                    // --- encryptableFieldMap ---
                    if (affectedEncryptableFields.isNotEmpty()) {
                        val fieldMapDoc = doc["encryptableFieldMap"] as? Document
                        if (fieldMapDoc != null) {
                            for (fieldName in affectedEncryptableFields.keys) {
                                val encryptedValue = fieldMapDoc[fieldName] as? String ?: continue
                                val plainId = tryDecryptId(masterSecret, entityClass, encryptedValue)
                                if (plainId != encryptedValue) {
                                    fieldMapDoc[fieldName] = plainId
                                    modified = true
                                }
                            }
                            if (modified) {
                                doc["encryptableFieldMap"] = fieldMapDoc
                            }
                        }
                    }

                    if (modified) {
                        collection.replaceOne(Document("_id", doc["_id"]), doc)
                        logger.info("Migrated document _id=${doc["_id"]} in collection '${collection.namespace.collectionName}'")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error during migration for entity '${entityClass.name}': ${e.message}", e)
            } finally {
                cursor.close()
            }
        }
    }

    /**
     * Attempts to decrypt [encryptedValue] (a Base64-encoded AES-GCM ciphertext) using the
     * [masterSecret].
     *
     * - If decryption succeeds (GCM tag verified), returns the plaintext ID string.
     * - If decryption fails for any reason (wrong key, already plaintext, corrupted data),
     *   `AES256.decrypt` returns the input bytes unchanged, so the re-encoded string will equal
     *   the original [encryptedValue] — in that case the original value is returned unmodified.
     *
     * @param masterSecret  The master secret used for decryption.
     * @param entityClass   The entity class, used as the HKDF derivation context.
     * @param encryptedValue The value stored in the document (may be ciphertext or already plaintext).
     * @return The decrypted plaintext ID, or [encryptedValue] unchanged if decryption failed.
     */
    private fun tryDecryptId(
        masterSecret: String,
        entityClass: Class<out Encryptable<*>>,
        encryptedValue: String,
    ): String {
        return try {
            val decoded = encryptedValue.decode64()
            val decryptedBytes = AES256.decrypt(masterSecret, entityClass, decoded)
            // AES256.decrypt returns the same reference on failure — identity check is intentional.
            if (decryptedBytes === decoded) {
                // Decryption failed (wrong key or already plaintext) — leave unchanged.
                encryptedValue
            } else {
                decryptedBytes.decodeToString()
            }
        } catch (_: Exception) {
            // Base64 decode failure → the value was never encrypted (it's a raw plaintext ID).
            encryptedValue
        }
    }
}



