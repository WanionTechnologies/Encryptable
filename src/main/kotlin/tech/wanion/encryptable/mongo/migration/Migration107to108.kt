package tech.wanion.encryptable.mongo.migration

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.bson.Document
import org.bson.types.Binary
import org.slf4j.LoggerFactory
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.MasterSecretHolder
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.encodeURL64
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.metadata

class Migration107to108 : Migration {
    /** Logger instance for logging migration progress and any issues encountered during the migration process. */
    private val logger = LoggerFactory.getLogger(Migration107to108::class.java)

    /** The source version for this migration, indicating that it will update the database from version 1.0.7 to 1.0.8. */
    override fun fromVersion(): String = "1.0.7"

    /** The target version for this migration, indicating that it will update the database from version 1.0.7 to 1.0.8. */
    override fun toVersion(): String = "1.0.8"

    /**
     * This migration renames the 'gridFsFields' field to 'storageFields' in all documents across all collections in all databases.
     * It iterates through each database and collection, finds documents containing the 'gridFsFields' field, and updates them by renaming the field to 'storageFields'.
     * The migration ensures that any existing data in the 'gridFsFields' field is preserved and transferred to the new 'storageFields' field.
     */
    override fun migrateSchema() {
        val client = MongoClients.create()
        try {
            val dbNames = client.listDatabaseNames()
            for (dbName in dbNames) {
                val db: MongoDatabase = client.getDatabase(dbName)
                val collections = db.listCollectionNames()
                collections.parallelForEach { collectionName ->
                    val collection: MongoCollection<Document> = db.getCollection(collectionName)
                    val query = Document("gridFsFields", Document($$"$exists", true))
                    val cursor = collection.find(query).iterator()
                    try {
                        while (cursor.hasNext()) {
                            val doc = cursor.next()
                            val gridFsFields = doc["gridFsFields"]
                            if (gridFsFields != null) {
                                doc["storageFields"] = gridFsFields
                                doc.remove("gridFsFields")
                                collection.replaceOne(Document("_id", doc["_id"]), doc)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e.message)
                    } finally {
                        cursor.close()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e.message)
        } finally {
            client.close()
        }
    }

    /**
     * This method migrates the data for all Encryptable entities that have byte array fields annotated with @Encrypt.
     * It iterates through all repositories to find entity classes with byte array fields that require migration, decrypts the existing data using the old secret (derived from the entity ID), and re-encrypts it using the master secret before storing it back.
     * The migration ensures that only relevant fields are processed and that any issues encountered during decryption or re-encryption are logged for troubleshooting.
     */
    override fun migrateData() {
        val storageHandler = getBean(StorageHandler::class.java)
        // Use EncryptableContext to get all repositories, then get entity classes from them
        val repositories = EncryptableContext.getAllRepositories()
        repositories.parallelForEach { repository ->
            val entityClass = repository.getTypeClass()
            val metadata = Encryptable.getMetadataFor(entityClass)
            // If the entity class is marked as isolated, skip it since it will not have affected ByteArray fields that need to be migrated.
            if (metadata.isolated) return@parallelForEach
            val byteArrayFields = metadata.byteArrayFields
            // If there are no byte array fields, skip this entity class
            if (byteArrayFields.isEmpty()) return@parallelForEach
            val encryptFields = byteArrayFields.values.filter { it.isAnnotationPresent(Encrypt::class.java) }
            // If there are no byte array fields annotated with @Encrypt, skip this entity class since it won't have any fields that need to be migrated.
            if (encryptFields.isEmpty()) return@parallelForEach

            val masterSecret = MasterSecretHolder.getMasterSecret()

            val collection = repository.getMongoCollection()
            val cursor = collection.find().iterator()
            try {
                while (cursor.hasNext()) {
                    val doc = cursor.next()
                    val entityId = doc["_id"]
                    val id: ByteArray
                    try {
                        id = when (entityId) {
                            is Binary -> entityId.data
                            else -> throw IllegalStateException("Entity ID is not a Binary type, cannot perform migration for document with _id: $entityId")
                        }
                    } catch (e: Exception) {
                        logger.error(e.message)
                        return@parallelForEach
                    }

                    // The old secret is derived from the entity ID, so we encode it to URL-safe Base64 to get the string representation of the old secret.
                    val secret = id.encodeURL64()

                    encryptFields.forEach { field ->
                        val fieldName = field.name
                        val storage = storageHandler.getStorageForField(field)
                        try {
                            val fieldBytes = when (val fieldValue = doc[fieldName]) {
                                is Binary -> fieldValue.data
                                null -> return@forEach // If the field is null, skip it since there is no data to migrate.
                                else -> throw IllegalStateException("Field '$fieldName' in document with _id: $entityId is not a Binary type, cannot perform migration.")
                            }
                            val reference = storage.createReference(fieldBytes)

                            val bytes = when (reference) {
                                is Any -> storage.read(field.metadata, reference)
                                else -> fieldBytes // If reference is null, it means the data was stored inline.
                            }

                            if (bytes == null)
                                throw IllegalStateException("No data found for field '$fieldName' in document with _id: $entityId, cannot perform migration.")

                            // Decrypt the existing data using the old secret (derived from the entity ID)
                            val decryptedBytes = AES256.decrypt(secret, entityClass, bytes)

                            // Decryption returns the same bytes if decryption fails (e.g., due to incorrect key or corrupted data), so we can check for that to detect decryption failures.
                            if (decryptedBytes === bytes)
                                throw IllegalStateException("Decryption failed for field '$fieldName' in document with _id: $entityId, cannot perform migration.")
                            // then re-encrypt it using the master secret before storing it back.
                            val reEncryptedBytes = AES256.encrypt(masterSecret, entityClass, decryptedBytes)

                            val newFieldBytes = when (reference) {
                                is Any -> storage.createWithBytesReference(field.metadata, reEncryptedBytes) // If there was an existing reference, create a new one for the re-encrypted bytes.
                                else -> reEncryptedBytes // If there was no existing reference, it means the data was stored inline, so we can just use the re-encrypted bytes directly.
                            }

                            doc[fieldName] = Binary(newFieldBytes)

                            collection.replaceOne(Document("_id", doc["_id"]), doc)

                            // After successfully migrating the data, we can delete the old reference if it existed.
                            if (reference != null)
                                storage.delete(field.metadata, reference)

                        } catch (e: Exception) {
                            logger.error(e.message)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e.message)
            } finally {
                cursor.close()
            }
        }
    }
}