package tech.wanion.encryptable.mongo.migration

import com.mongodb.client.MongoCollection
import org.bson.Document
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.storage.Sliced
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.getBean
import java.nio.ByteBuffer

class Migration110to111 : Migration  {
    /** Logger instance for logging migration progress and any issues encountered during the migration process. */
    private val logger = LoggerFactory.getLogger(this::class.java)

    /** The source version for this migration */
    override fun fromVersion(): String = "1.1.0"

    /** The target version for this migration */
    override fun toVersion(): String = "1.2.0"

    /**
     * No schema changes are required for 1.2.0; the document structure is identical.
     */
    override fun migrateSchema() {
        // No schema changes needed.
    }

    /**
     * Migrates all MongoDB documents containing @Sliced ByteArray fields from the old reference header format (4-byte Int size prefix)
     * to the new format (8-byte Long size prefix) introduced in Encryptable 1.2.0.
     *
     * This migration is required to support theoretical file sizes up to 3PB, far beyond the previous ~2GB limit imposed by Int.MAX_VALUE.
     *
     * The migration works as follows:
     * - Iterates all repositories and their entity classes.
     * - For each entity, finds ByteArray fields annotated with @Sliced.
     * - For each document in the collection:
     *   - For each relevant field:
     *     - If the field is present in storageFields and is a Binary, extracts the reference bytes.
     *     - If the reference bytes are in the old format (4-byte size prefix), migrates:
     *         - Reads the 4-byte size as Int.
     *         - Allocates a new array: 8 bytes for size (Long), then the references.
     *         - Copies the references from offset 4 to offset 8.
     *         - Replaces the field in the document with the new Binary.
     *   - If any field was modified, replaces the document in the collection.
     *
     * Efficiency:
     * - Only updates documents and fields that require migration, minimizing write load.
     * - Uses parallel processing at the repository level for speed.
     * - Processes one document at a time to avoid high memory usage.
     *
     * Edge cases handled:
     * - Skips fields with invalid reference data (less than 4 bytes).
     * - Skips documents/fields already in the new format.
     * - Logs errors and continues on failure.
     */
    override fun migrateData() {
        val repositories = EncryptableContext.getAllRepositories()

        val storageHandler = getBean(StorageHandler::class.java)

        repositories.parallelForEach { repository ->
            val entityClass = repository.getTypeClass()
            val metadata = Encryptable.getMetadataFor(entityClass)

            val byteArrayFields = metadata.byteArrayFields

            // only proceed if there are ByteArrays fields
            if (byteArrayFields.isEmpty()) return@parallelForEach

            val slicedFields = byteArrayFields.filter { it.value.isAnnotationPresent(Sliced::class.java) }

            // only proceed if a ByteArray field is annotated with @Sliced
            if (slicedFields.isEmpty()) return@parallelForEach

            val fieldStorage = slicedFields.values.associateWith { storageHandler.getStorageForField(it) }

            val collection: MongoCollection<Document> = repository.getMongoCollection()
            val cursor = collection.find().iterator()
            try {
                while (cursor.hasNext()) {
                    val doc = cursor.next()
                    var modified = false

                    fieldStorage.forEach { (field, storage) ->
                        val storageFields = doc["storageFields"] as? List<*> ?: return@forEach

                        // if this field is not marked as a storage entry;
                        // either the field was not set or it is an inline storage, skip it.
                        if (!storageFields.contains(field.name))
                            return@forEach

                        // the stored reference should be a Binary.
                        val referenceBinary = doc[field.name] as? Binary ?: return@forEach

                        // getting the actual ByteArray from the Binary.
                        val referenceBytes = referenceBinary.data
                        
                        if (referenceBytes.size < 4) {
                            logger.error("Reference data for field '${field.name}' in document with _id: ${doc["_id"]} is too short to contain a valid reference, skipping migration for this field.")
                            return@forEach
                        }

                        val refLen = storage.referenceLength

                        val needsUpdating = ((referenceBytes.size - 8) % refLen != 0)

                        if (!needsUpdating)
                            return@forEach

                        val size = ByteBuffer.wrap(referenceBytes, 0, 4).int

                        val updatedReferences = ByteArray(8 + (referenceBytes.size - 4))
                        ByteBuffer.wrap(updatedReferences, 0, 8).putLong(size.toLong())
                        System.arraycopy(referenceBytes, 4, updatedReferences, 8, referenceBytes.size - 4)

                        doc[field.name] = Binary(updatedReferences)

                        modified = true
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
}