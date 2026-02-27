package tech.wanion.encryptable.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.first4KBChecksum
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.getField
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@Component
class StorageHandler {
    /**
     * Logger instance for logging storage-related operations and errors.
     */
    private val logger = LoggerFactory.getLogger(StorageHandler::class.java)

    /**
     * A thread-safe cache mapping Java Fields to their corresponding IStorage implementations.
     * This cache is used to quickly determine which storage implementation to use for a given field based on its annotations.
     * The cache is populated on demand when a field is accessed for the first time, using the getStorageForField method to determine the appropriate storage implementation.
     */
    private val storageCache = ConcurrentHashMap<Field, IStorage<*>>()

    /**
     * The default storage implementation (GridFSStorage) used as a fallback when no specific storage implementation is found for a field or when an error occurs while retrieving the specified storage bean.
     */
    private val gridFSStorage = getBean(GridFSStorage::class.java)

    /**
     * Retrieves the byte array data for the specified field from the appropriate storage system (e.g., GridFS) and sets it on the given Encryptable object.
     * This method is responsible for loading the field data from storage when an Encryptable object is being accessed and its associated fields need to be retrieved.
     * It checks if the field is designated as a storage field, retrieves the reference bytes, determines the appropriate storage implementation, reads the data from storage, decrypts it if necessary, and sets it on the Encryptable object.
     *
     * @param encryptable The Encryptable object whose field is being loaded from storage.
     * @param fieldName The name of the field that is being loaded from storage.
     */
    fun get(encryptable: Encryptable<*>, fieldName: String) {
        // Only proceed if this field is marked as a storage field
        // if it doesn't contain probably it is inline.
        val storageFields = getStorageFields(encryptable)
        if (!storageFields.contains(fieldName))
            return

        val storageFieldIdMap = getStorageFieldIdMap(encryptable)

        if (storageFieldIdMap.contains(fieldName))
            return // Already loaded, no need to load again

        val metadata = Encryptable.getMetadataFor(encryptable)

        try {
            val field = getField(encryptable, fieldName)

            val referenceBytes = getReferenceBytes(encryptable, fieldName) ?:
                throw IllegalStateException("No reference bytes found for field '$fieldName' in ${encryptable::class.java.name}, unable to load from storage.")

            // Cache the reference bytes in the storageFieldIdMap for future reference (e.g., for updates or deletes)
            storageFieldIdMap[fieldName] = referenceBytes

            val storage = getStorageForField(field)
            val reference = storage.createReference(referenceBytes)
                ?: throw IllegalStateException("Invalid reference bytes for field '$fieldName' in ${encryptable::class.java.name}, unable to create storage reference. Reference bytes: ${referenceBytes.contentToString()}")

            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)

            // If secret is null, means that this entity was not loaded with findBySecret,
            // we don't have the secret... which means we can't decrypt the field.
            // instead of throwing an exception, we just return.
            // why? at least we populated storageFieldIdMap.
            if (encryptField && Encryptable.getUnsafeSecretOf(encryptable) == null) {
                logger.warn("Field '$fieldName' in ${encryptable::class.java.name} is encrypted but no secret is available to decrypt it. Returning without loading the field.")
                return
            }

            val rawBytes = storage.read(reference)
                ?: throw IllegalStateException("No data found for reference of field '$fieldName' in ${encryptable::class.java.name}")

            val bytes = if (encryptField) AES256.decrypt(
                metadata.strategies.getSecretFor(encryptable),
                encryptable.javaClass,
                rawBytes
            ) else rawBytes

            field.set(encryptable, bytes)

            if (!Encryptable.isNew(encryptable) && !Encryptable.hasErrored(encryptable))
                Encryptable.updateEntityInfo(encryptable, fieldName to bytes.first4KBChecksum())
        } catch (e: Exception) {
            logger.error("Error reading from storage for field '$fieldName' in ${encryptable::class.java.name}: ${e.message}")
        }
    }

    /**
     * Updates the byte array data for the specified field on the given Encryptable object and handles the necessary storage operations (e.g., creating/updating/deleting entries in GridFS).
     * This method is responsible for managing the storage of field data when an Encryptable object's fields are being updated.
     * It checks if the new byte array value exceeds the inline storage threshold, determines whether to store it in the storage system or inline, handles encryption if necessary, and updates the Encryptable's metadata accordingly.
     *
     * @param encryptable The Encryptable object whose field is being updated.
     * @param fieldName The name of the field that is being updated.
     * @param newBytes The new byte array value that is being set on the field.
     * @return A boolean indicating whether the caller should proceed with setting the new value on the field (true) or if the StorageHandler has already handled it (false).
     */
    private fun set(encryptable: Encryptable<*>, fieldName: String, newBytes: ByteArray?) {
        val metadata = Encryptable.getMetadataFor(encryptable)
        val field = getField(encryptable, fieldName)

        // Get the storage implementation for this field (could be GridFS or a custom storage)
        val fieldStorage = getStorageForField(field)

        // Get old bytes.
        val oldBytes = field.get(encryptable) as? ByteArray?

        if (oldBytes == null && newBytes == null) return // No change, both null

        val storageFields = getStorageFields(encryptable)
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)

        var oldReference: Any? = null

        if (oldBytes != null) {
            if (oldBytes.contentEquals(newBytes)) return
            val referenceBytes = getReferenceBytes(encryptable, fieldName)
            oldReference = fieldStorage.createReference(referenceBytes)
            // Get the Reference from the oldBytes or from the map.
            if (oldReference != null) {
                // if it could find a reference, means that it had a file stored.
                // delete the old file from Storage
                storageFields.remove(fieldName)
                storageFieldIdMap.remove(fieldName)
            }
            if (newBytes == null) {
                field.set(encryptable, null)
                if (oldReference != null)
                    fieldStorage.delete(oldReference)
                return
            }
        }

        newBytes as ByteArray

        val isBig = newBytes.size >= EncryptableConfig.storageThreshold // 1KB threshold

        if (isBig) {
            // Storing a large field: save to Storage and update metadata
            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            val bytesToStore =
                if (encryptField) AES256.encrypt(metadata.strategies.getSecretFor(encryptable), encryptable::class.java, newBytes) else newBytes

            // if it throws on create... this may not be enough.
            // needs further testing.
            try {
                val reference = fieldStorage.create(bytesToStore)
                val referenceBytes = fieldStorage.bytesFromReference(reference)
                if (!storageFields.contains(fieldName))
                    storageFields.add(fieldName)
                storageFieldIdMap[fieldName] = referenceBytes
            } catch (e: Exception) {
                Encryptable.setErrored(encryptable)
                throw e
            }
        }
        field.set(encryptable, newBytes)
        // if it has errored, it will not be updated.
        if (oldReference != null && !Encryptable.hasErrored(encryptable))
            fieldStorage.delete(oldReference)
    }

    /**
     * Prepares the given Encryptable object for storage by processing its byte array fields and determining whether they should be stored inline or in the storage system (e.g., GridFS).
     * This method is responsible for checking the size of each byte array field, encrypting it if necessary, and storing it in the appropriate storage system if it exceeds the inline storage threshold.
     * It updates the Encryptable's metadata to reflect which fields are stored in the storage system and their corresponding references.
     *
     * @param encryptable The Encryptable object that is being prepared for storage.
     */
    private fun prepare(encryptable: Encryptable<*>) {
        val metadata = Encryptable.getMetadataFor(encryptable)

        // Process byte array fields that need GridFS storage
        val storageFields = getStorageFields(encryptable)
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)

        metadata.byteArrayFields.entries.parallelForEach { (fieldName, field) ->
            // Get current byte array value, skip if null
            val bytes = field.get(encryptable) as? ByteArray? ?: return@parallelForEach
            // If field is already processed, or if size < storageThreshold, skip
            if (storageFields.contains(fieldName) || bytes.size < EncryptableConfig.storageThreshold)
                return@parallelForEach

            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            val bytesToStore = if (encryptField) AES256.encrypt(metadata.strategies.getSecretFor(encryptable), encryptable::class.java, bytes) else bytes
            val storage = getStorageForField(field)
            // this is not as critical as in set.
            val reference = storage.create(bytesToStore)
            val referenceBytes = storage.bytesFromReference(reference)
            storageFieldIdMap[fieldName] = referenceBytes
            storageFields.add(fieldName)
        }
    }

    /**
     * Retrieves the appropriate IStorage implementation for the given field based on its annotations.
     * If the field has a custom storage annotation, it attempts to retrieve the specified storage bean from the Spring context.
     * If no annotation is present or if there is an error retrieving the specified storage bean, it falls back to using the default GridFSStorage implementation.
     *
     * @param field The Java Field for which to determine the storage implementation.
     * @return The IStorage implementation to use for the given field.
     */
    @Suppress("UNCHECKED_CAST")
    fun getStorageForField(field: Field): IStorage<Any> {
        return storageCache.computeIfAbsent(field) { f ->
            getStorageClassFromField(f)?.let { storageClass ->
                try {
                    // Attempt to retrieve the storage bean from the Spring context
                    val storageBean: Any = getBean<Any>(storageClass as Class<Any>)
                    storageBean as? IStorage<Any>
                        ?: throw IllegalStateException("Bean of type ${storageClass.name} does not implement IStorage, falling back to GridFSStorage")
                } catch (e: Exception) {
                    logger.error(e.message)
                    gridFSStorage // fallback on error
                }
            } ?: gridFSStorage // fallback if no @Storage annotation
        } as IStorage<Any>
    }

    /**
     * Overloaded method to retrieve the appropriate IStorage implementation for a given field name on an Encryptable object.
     * This method abstracts the logic of retrieving the Field object for the specified field name and then determining the appropriate storage implementation based on its annotations.
     *
     * @param encryptable The Encryptable object whose field is being accessed.
     * @param fieldName The name of the field for which to determine the storage implementation.
     * @return The IStorage implementation to use for the specified field.
     */
    fun getStorageForField(encryptable: Encryptable<*>, fieldName: String): IStorage<Any> {
        val field = getField(encryptable, fieldName)
        return getStorageForField(field)
    }

    /**
     * Checks the annotations of the given field and returns the class specified in the @Storage meta-annotation, if present.
     * @param field the java.lang.reflect.Field to inspect
     * @return the class specified in @Storage, or null if not found
     */
    fun getStorageClassFromField(field: Field): Class<out IStorage<*>>? {
        for (annotation in field.annotations) {
            val annotationClass = annotation.annotationClass.java
            val storageAnnotation = annotationClass.getAnnotation(Storage::class.java)
            if (storageAnnotation != null)
                return storageAnnotation.storageClass.java
        }
        return null
    }

    /**
     * Helper method to get the list of storage fields from the Encryptable's metadata.
     * This list contains the names of fields that are designated for storage in the storage system (e.g., GridFS) when they exceed the inline storage threshold.
     *
     * @param encryptable The Encryptable object whose storage fields are being accessed.
     * @return The list of field names that are designated for storage in the storage system.
     */
    fun getStorageFields(encryptable: Encryptable<*>): MutableList<String> = encryptable.getField("storageFields")

    /**
     * Helper method to get the storageFieldIdMap from the Encryptable's metadata.
     * This map is used to store references (e.g., ObjectIds) for fields that are stored in the storage system (e.g., GridFS).
     *
     * @param encryptable The Encryptable object whose storageFieldIdMap is being accessed.
     * @return The ConcurrentHashMap that maps field names to their corresponding byte array references in the storage system.
     */
    fun getStorageFieldIdMap(encryptable: Encryptable<*>): ConcurrentHashMap<String, ByteArray> = encryptable.getField("storageFieldIdMap")

    /**
     * Helper method to get the Field object for a given field name from the Encryptable's metadata.
     * This method abstracts the logic of retrieving the Field object for byte array fields, ensuring that it is properly accessed and cached if necessary.
     *
     * @param encryptable The Encryptable object whose field is being accessed.
     * @param fieldName The name of the field whose Field object is being retrieved.
     * @return The Field object associated with the specified field name.
     * @throws IllegalArgumentException if the specified field name is not a byte array field in the Encryptable's metadata.
     */
    fun getField(encryptable: Encryptable<*>, fieldName: String): Field =
        Encryptable.getMetadataFor(encryptable).byteArrayFields[fieldName] ?: throw IllegalArgumentException("Field '$fieldName' is not a byte array field in ${encryptable::class.java.name}")

    /**
     * Helper method to get the byte array reference for a given field name from the Encryptable's metadata.
     * This method abstracts the logic of retrieving the byte array reference for fields that are stored in the storage system (e.g., GridFS),
     * ensuring that it is properly accessed and cached if necessary.
     *
     * @param encryptable The Encryptable object whose field reference is being accessed.
     * @param fieldName The name of the field whose byte array reference is being retrieved.
     * @return The byte array reference associated with the specified field name, or null if no reference is found.
     */
    fun getReferenceBytes(encryptable: Encryptable<*>, fieldName: String): ByteArray? {
        val fieldData = Encryptable.getMetadataFor(encryptable).byteArrayFields[fieldName]?.get(encryptable) as? ByteArray ?: return null
        var fieldReference = fieldData.takeIf { it.size <= 16 } // Assuming a possible ID size threshold (e.g., 12 bytes for ObjectId)
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)
        if (fieldReference != null)
            storageFieldIdMap[fieldName] = fieldReference
        else
            fieldReference = storageFieldIdMap[fieldName].takeIf { it != null && it.size <= 16 }
        return fieldReference
    }
}