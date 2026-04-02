package tech.wanion.encryptable.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tech.wanion.encryptable.config.EncryptableConfig
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.Limited.parallelMap
import tech.wanion.encryptable.util.extensions.first4KBChecksum
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.metadata
import tech.wanion.encryptable.util.extensions.readField
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

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
     * Retrieves the byte array data for the specified field from the appropriate storage system
     * (e.g., GridFS) and sets it on the given Encryptable object.
     *
     * Dispatches to [readFromSlices] for [@Sliced] fields (parallel fetch + decrypt with direct
     * index placement) or [readFromStorage] for regular single-reference fields.
     *
     * @param encryptable The Encryptable object whose field is being loaded from storage.
     * @param fieldName The name of the field that is being loaded from storage.
     */
    fun get(encryptable: Encryptable<*>, fieldName: String) {
        // Only proceed if this field is marked as a storage field.
        // if it doesn't contain probably it is inline.
        val storageFields = getStorageFields(encryptable)
        if (!storageFields.contains(fieldName))
            return

        val storageFieldIdMap = getStorageFieldIdMap(encryptable)
        if (storageFieldIdMap.contains(fieldName))
            return // Already loaded, no need to load again

        val field = getField(encryptable, fieldName)

        if (field.isAnnotationPresent(Sliced::class.java)) {
            validateSlicedAnnotation(field, encryptable)
            readFromSlices(encryptable, fieldName, field, storageFieldIdMap)
        } else {
            readFromStorage(encryptable, fieldName, field, storageFieldIdMap)
        }
    }

    /**
     * Reads a regular (non-sliced) field from storage: fetches the single ciphertext blob,
     * decrypts it if [@Encrypt] is present, and sets it on the entity.
     *
     * @param encryptable The entity whose field is being loaded.
     * @param fieldName The name of the field.
     * @param field The reflected [Field] object.
     * @param storageFieldIdMap The entity's reference cache map.
     */
    private fun readFromStorage(
        encryptable: Encryptable<*>,
        fieldName: String,
        field: Field,
        storageFieldIdMap: ConcurrentHashMap<String, ByteArray>
    ) {
        val metadata = Encryptable.getMetadataFor(encryptable)
        try {
            val referenceBytes = getReferenceBytes(encryptable, fieldName)
                ?: throw IllegalStateException("No reference bytes found for field '$fieldName' in ${encryptable::class.java.name}, unable to load from storage.")

            storageFieldIdMap[fieldName] = referenceBytes

            val storage = getStorageForField(field)
            val reference = storage.createReference(referenceBytes)
                ?: throw IllegalStateException("Invalid reference bytes for field '$fieldName' in ${encryptable::class.java.name}, unable to create storage reference. Reference bytes: ${referenceBytes.contentToString()}")

            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)

            // If secret is null, means that this entity was not loaded with findBySecret —
            // we don't have the secret to decrypt. Return after caching the reference.
            if (encryptField && !Encryptable.canDecrypt(encryptable)) {
                logger.warn("Field '$fieldName' in ${encryptable::class.java.name} is encrypted but no secret is available to decrypt it. Returning without loading the field.")
                return
            }

            val rawBytes = storage.read(field.metadata, reference)
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
     * Reads a [@Sliced] field from storage using fully parallel fetch and decrypt.
     *
     * Strategy:
     * 1. Parse the concatenated reference [ByteArray] to extract [SlicedResult.originalLength]
     *    and the ordered list of per-slice references — no slice is fetched yet.
     * 2. Pre-allocate a single output [ByteArray] of exactly [SlicedResult.originalLength] bytes.
     * 3. Fetch all slices in parallel (I/O-bound, unlimited virtual threads via `limited = false`).
     *    Each fetch+decrypt writes its plaintext directly at the correct byte offset in the
     *    pre-allocated output — no intermediate buffers, no reassembly step.
     * 4. Set the fully assembled [ByteArray] on the field and register it for dirty tracking.
     *
     * If any slice fails to fetch or decrypt, the error is logged and the field is left unset
     * (same fail-safe behavior as [readFromStorage]).
     *
     * @param encryptable The entity whose field is being loaded.
     * @param fieldName The name of the field.
     * @param field The reflected [Field] object.
     * @param storageFieldIdMap The entity's reference cache map.
     */
    private fun readFromSlices(
        encryptable: Encryptable<*>,
        fieldName: String,
        field: Field,
        storageFieldIdMap: ConcurrentHashMap<String, ByteArray>
    ) {
        val metadata = Encryptable.getMetadataFor(encryptable)
        val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)

        if (encryptField && !Encryptable.canDecrypt(encryptable)) {
            logger.warn("Field '$fieldName' in ${encryptable::class.java.name} is encrypted but no secret is available to decrypt it. Returning without loading the field.")
            return
        }

        try {
            val slicedResult = getSlices(encryptable, fieldName)
                ?: throw IllegalStateException("No slice references found for field '$fieldName' in ${encryptable::class.java.name}.")

            // Capture the raw concatenated reference bytes before any field mutation,
            // so we can cache them below regardless of what getSlices read them from.
            val rawReferenceBytes = getStorageFieldIdMap(encryptable)[fieldName]
                ?: (field.get(encryptable) as? ByteArray)
                ?: throw IllegalStateException("Reference bytes vanished for field '$fieldName' in ${encryptable::class.java.name}.")

            val storage = getStorageForField(field)
            val originalLength = slicedResult.originalLength
            if (originalLength > Int.MAX_VALUE)
                throw IllegalStateException("Original data length $originalLength for field '$fieldName' in ${encryptable::class.java.name} exceeds maximum supported size of 2 GB.")

            val output = ByteArray(slicedResult.originalLength.toInt())
            val sliceSizeBytes = field.getAnnotation(Sliced::class.java).sizeMB * 1024 * 1024

            // Fetch and decrypt all slices in parallel.
            // Each slice writes to a non-overlapping region of output — no write conflicts.
            (0 until slicedResult.slices.size).parallelForEach { i ->
                val referenceBytes = slicedResult.slices[i]

                val reference = storage.createReference(referenceBytes)
                    ?: throw IllegalStateException("Invalid reference for slice $i of field '$fieldName' in ${encryptable::class.java.name}.")

                val rawBytes = storage.read(field.metadata, reference)
                    ?: throw IllegalStateException("No data for slice $i of field '$fieldName' in ${encryptable::class.java.name}.")

                val sliceBytes = if (encryptField) AES256.decrypt(
                    metadata.strategies.getSecretFor(encryptable),
                    encryptable.javaClass,
                    rawBytes
                ) else rawBytes

                sliceBytes.copyInto(output, destinationOffset = i * sliceSizeBytes)
            }

            field.set(encryptable, output)

            if (!Encryptable.isNew(encryptable) && !Encryptable.hasErrored(encryptable))
                Encryptable.updateEntityInfo(encryptable, fieldName to output.first4KBChecksum())

            // Cache the concatenated reference bytes (not the plaintext) for future dirty-check reads.
            storageFieldIdMap[fieldName] = rawReferenceBytes
        } catch (e: Exception) {
            logger.error("Error reading slices for field '$fieldName' in ${encryptable::class.java.name}: ${e.message}")
        }
    }

    /**
     * Sets the given byte array on the specified field of the Encryptable object, handling storage logic for large byte arrays.
     * If the new byte array exceeds the storage threshold, it is stored in the appropriate storage system (e.g., GridFS) and a reference is set on the field instead.
     * If the new byte array is null or below the storage threshold, it is set directly on the field.
     * The method also handles cleanup of old stored data if the field previously contained a large byte array that was stored in the storage system.
     *
     * @param encryptable The Encryptable object whose field is being updated.
     * @param fieldName The name of the field being updated.
     * @param newBytes The new byte array to set on the field, or null to clear it.
     */
    private fun set(encryptable: Encryptable<*>, fieldName: String, newBytes: ByteArray?) {
        val metadata = Encryptable.getMetadataFor(encryptable)
        val field = getField(encryptable, fieldName)
        val fieldStorage = getStorageForField(field)
        val oldBytes = field.get(encryptable) as? ByteArray?

        if (oldBytes == null && newBytes == null) return // No change, both null

        val storageFields = getStorageFields(encryptable)
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)
        val slicedField = field.isAnnotationPresent(Sliced::class.java)
        if (slicedField) validateSlicedAnnotation(field, encryptable)

        // Guard: if newBytes are the currently stored reference bytes for this field,
        // this is processFields(true) restoring the field to its reference state after
        // encryption/serialization — not a user-initiated data update. Let it proceed
        // without any storage operation; the aspect will set the field directly.
        val storedReference = storageFieldIdMap[fieldName]
        if (newBytes != null && storedReference != null && newBytes.contentEquals(storedReference)) return

        // Capture old concatenated reference bytes before clearing the map entry,
        // so we can delete old slices after the new ones are safely stored.
        var oldReferenceBytes: ByteArray? = null
        var oldSingleReference: Any? = null

        if (oldBytes != null) {
            if (oldBytes.contentEquals(newBytes)) return

            if (slicedField) {
                // For sliced fields the storageFieldIdMap holds the full concatenated reference.
                oldReferenceBytes = storageFieldIdMap[fieldName]
            } else {
                val refBytes = getReferenceBytes(encryptable, fieldName)
                oldSingleReference = fieldStorage.createReference(refBytes)
            }

            val hadStoredData = if (slicedField) oldReferenceBytes != null else oldSingleReference != null
            if (hadStoredData) {
                storageFields.remove(fieldName)
                storageFieldIdMap.remove(fieldName)
            }

            if (newBytes == null) {
                field.set(encryptable, null)
                // Atomic replace: new is null → just delete old data.
                if (slicedField && oldReferenceBytes != null)
                    deleteSlices(field, fieldStorage, oldReferenceBytes)
                else if (oldSingleReference != null)
                    fieldStorage.delete(field.metadata, oldSingleReference)
                return
            }
        }

        newBytes as ByteArray
        val isBig = newBytes.size >= EncryptableConfig.storageThreshold

        if (isBig) {
            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            try {
                val combinedReferenceBytes = if (slicedField) {
                    val sizeMB = field.getAnnotation(Sliced::class.java).sizeMB
                    storeAsSlices(newBytes, field, fieldStorage, sizeMB, encryptField, encryptable, metadata)
                } else {
                    val bytesToStore = if (encryptField) AES256.encrypt(
                        metadata.strategies.getSecretFor(encryptable), encryptable::class.java, newBytes
                    ) else newBytes
                    val reference = fieldStorage.create(field.metadata, bytesToStore)
                    fieldStorage.bytesFromReference(reference)
                }
                if (!storageFields.contains(fieldName)) storageFields.add(fieldName)
                storageFieldIdMap[fieldName] = combinedReferenceBytes
            } catch (e: Exception) {
                Encryptable.setErrored(encryptable)
                throw e
            }
        }

        field.set(encryptable, newBytes)

        // Atomic replace: delete old data only after new data is safely stored.
        if (!Encryptable.hasErrored(encryptable)) {
            if (slicedField && oldReferenceBytes != null)
                deleteSlices(field, fieldStorage, oldReferenceBytes)
            else if (oldSingleReference != null)
                fieldStorage.delete(field.metadata, oldSingleReference)
        }
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
        val storageFields = getStorageFields(encryptable)
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)

        metadata.byteArrayFields.entries.parallelForEach { (fieldName, field) ->
            val bytes = field.get(encryptable) as? ByteArray? ?: return@parallelForEach
            if (storageFields.contains(fieldName) || bytes.size < EncryptableConfig.storageThreshold)
                return@parallelForEach

            val encryptField = metadata.encryptable && field.isAnnotationPresent(Encrypt::class.java)
            val storage = getStorageForField(field)

            val combinedReferenceBytes = if (field.isAnnotationPresent(Sliced::class.java)) {
                validateSlicedAnnotation(field, encryptable)
                val sizeMB = field.getAnnotation(Sliced::class.java).sizeMB
                storeAsSlices(bytes, field, storage, sizeMB, encryptField, encryptable, metadata)
            } else {
                val bytesToStore = if (encryptField) AES256.encrypt(
                    metadata.strategies.getSecretFor(encryptable), encryptable::class.java, bytes
                ) else bytes
                val reference = storage.create(field.metadata, bytesToStore)
                storage.bytesFromReference(reference)
            }

            storageFieldIdMap[fieldName] = combinedReferenceBytes
            storageFields.add(fieldName)
        }
    }

    /**
     * Deletes the stored data for the specified field from the storage system (e.g., GridFS) and updates the Encryptable's metadata accordingly.
     * For [@Sliced] fields, all N slice references are deleted in parallel.
     *
     * @param encryptable The Encryptable object whose field data is being deleted from storage.
     * @param fieldName The name of the field whose data is being deleted from storage.
     */
    fun delete(encryptable: Encryptable<*>, fieldName: String) {
        val field = getField(encryptable, fieldName)
        val storage = getStorageForField(field)

        if (field.isAnnotationPresent(Sliced::class.java)) {
            // For sliced fields the storageFieldIdMap holds the full concatenated reference.
            val referenceBytes = getStorageFieldIdMap(encryptable)[fieldName]
                ?: (field.get(encryptable) as? ByteArray)
                ?: return
            deleteSlices(field, storage, referenceBytes)
        } else {
            val referenceBytes = getReferenceBytes(encryptable, fieldName) ?: return
            val reference = storage.createReference(referenceBytes) ?: return
            storage.delete(field.metadata, reference)
        }
    }

    /**
     * Validates that a [@Sliced] annotation on a field has a valid sizeMB parameter (between 1 and 32).
     * Throws IllegalArgumentException if the value is out of range.
     *
     * @param field The field to validate
     * @param encryptable The Encryptable instance (for error reporting)
     * @throws IllegalArgumentException if sizeMB is not between 1 and 32
     */
    private fun validateSlicedAnnotation(field: Field, encryptable: Encryptable<*>) {
        val slicedAnnotation = field.getAnnotation(Sliced::class.java) ?: return
        val sizeMB = slicedAnnotation.sizeMB

        if (sizeMB !in 1..32) {
            throw IllegalArgumentException(
                "Invalid @Sliced configuration on field '${field.name}' in ${encryptable::class.java.name}: " +
                        "sizeMB must be between 1 and 32 MB, but got $sizeMB MB"
            )
        }
    }

    /**
     * Deletes all slices belonging to a [@Sliced] field from storage in parallel.
     * Parses the concatenated reference [ByteArray] and issues one delete per slice,
     * all concurrently via unlimited virtual threads (I/O-bound).
     *
     * @param field The reflected [Field].
     * @param storage The [IStorage] backend.
     * @param referenceBytes The concatenated reference [ByteArray] (8-byte header + N × refLen).
     */
    private fun deleteSlices(field: Field, storage: IStorage<Any>, referenceBytes: ByteArray) {
        val refLen = storage.referenceLength
        if (referenceBytes.size < 8 + refLen) return // malformed, nothing safe to delete
        val refsBytes = referenceBytes.size - 8
        if (refsBytes % refLen != 0) return // corrupted
        val sliceCount = refsBytes / refLen

        (0 until sliceCount).parallelForEach(false) { i ->
            val refBytes = referenceBytes.copyOfRange(8 + i * refLen, 8 + (i + 1) * refLen)
            val reference = storage.createReference(refBytes) ?: return@parallelForEach
            storage.delete(field.metadata, reference)
        }
    }

    /**
     * Splits [bytes] into slices of [sizeMB] MB each, encrypts every slice independently
     * (if [encryptField] is true), stores them all in parallel via [storage], and returns the
     * concatenated reference [ByteArray] ready to be persisted in the entity document.
     *
     * Reference layout:
     * ```
     * [0..7]                         → original plaintext length (Long)
     * [8 .. 8+refLen-1]              → slice 0 reference
     * [8+refLen .. 8+2*refLen-1]     → slice 1 reference
     * ...
     * ```
     *
     * Each slice is encrypted with its own random IV (AES-256-GCM), so every ciphertext is
     * independent. Slices are stored in parallel with unlimited virtual threads (I/O-bound).
     *
     * @param bytes The full plaintext [ByteArray] to slice and store.
     * @param field The reflected [Field] — used for metadata and storage routing.
     * @param storage The [IStorage] backend to write to.
     * @param sizeMB Slice size in MB (from [@Sliced]).
     * @param encryptField Whether to encrypt each slice with AES-256-GCM.
     * @param encryptable The owner entity — needed for key derivation when [encryptField] is true.
     * @param metadata The entity's cached metadata.
     * @return The concatenated reference [ByteArray] (8-byte header + N × refLen).
     */
    private fun storeAsSlices(
        bytes: ByteArray,
        field: Field,
        storage: IStorage<Any>,
        sizeMB: Int,
        encryptField: Boolean,
        encryptable: Encryptable<*>,
        metadata: Encryptable.Metadata
    ): ByteArray {
        val sliceSizeBytes = sizeMB * 1024 * 1024
        val sliceCount = (bytes.size + sliceSizeBytes - 1) / sliceSizeBytes // ceiling division

        // Slice the plaintext into chunks, encrypt each independently, store in parallel.
        val references: List<ByteArray> = (0 until sliceCount).toList().parallelMap { i ->
            val from = i * sliceSizeBytes
            val to = minOf(from + sliceSizeBytes, bytes.size)
            val slice = bytes.copyOfRange(from, to)

            val bytesToStore = if (encryptField) AES256.encrypt(
                metadata.strategies.getSecretFor(encryptable),
                encryptable::class.java,
                slice
            ) else slice

            storage.bytesFromReference(storage.create(field.metadata, bytesToStore))
        }

        // Build the concatenated reference: 8-byte length header + N references.
        val refLen = storage.referenceLength
        val result = ByteArray(8 + refLen * sliceCount)
        ByteBuffer.wrap(result, 0, 8).putLong(bytes.size.toLong())
        references.forEachIndexed { i, refBytes ->
            refBytes.copyInto(result, destinationOffset = 8 + i * refLen)
        }
        return result
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
     * Overloaded method to retrieve the appropriate IStorage implementation for a given field name based on the Encryptable class.
     * This method abstracts the logic of retrieving the Field object for the specified field name using the Encryptable class and then determining the appropriate storage implementation based on its annotations.
     *
     * @param encryptableClass The Class object of the Encryptable type whose field is being accessed.
     * @param fieldName The name of the field for which to determine the storage implementation.
     * @return The IStorage implementation to use for the specified field.
     */
    fun getStorageForField(encryptableClass: Class<out Encryptable<*>>, fieldName: String): IStorage<Any> {
        val field = getField(encryptableClass, fieldName)
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
    fun getStorageFields(encryptable: Encryptable<*>): MutableList<String> = encryptable.readField("storageFields")

    /**
     * Helper method to get the storageFieldIdMap from the Encryptable's metadata.
     * This map is used to store references (e.g., ObjectIds) for fields that are stored in the storage system (e.g., GridFS).
     *
     * @param encryptable The Encryptable object whose storageFieldIdMap is being accessed.
     * @return The ConcurrentHashMap that maps field names to their corresponding byte array references in the storage system.
     */
    fun getStorageFieldIdMap(encryptable: Encryptable<*>): ConcurrentHashMap<String, ByteArray> = encryptable.readField("storageFieldIdMap")

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
     * Overloaded method to get the Field object for a given field name from the Encryptable's metadata, using the Encryptable class directly.
     * This method abstracts the logic of retrieving the Field object for byte array fields based on the Encryptable class, ensuring that it is properly accessed and cached if necessary.
     *
     * @param encryptableClass The Class object of the Encryptable type whose field is being accessed.
     * @param fieldName The name of the field whose Field object is being retrieved.
     * @return The Field object associated with the specified field name.
     * @throws IllegalArgumentException if the specified field name is not a byte array field in the Encryptable's metadata for the given class.
     */
    fun getField(encryptableClass: Class<out Encryptable<*>>, fieldName: String): Field =
        Encryptable.getMetadataFor(encryptableClass).byteArrayFields[fieldName] ?: throw IllegalArgumentException("Field '$fieldName' is not a byte array field in ${encryptableClass.name}")

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
        val field = Encryptable.getMetadataFor(encryptable).byteArrayFields[fieldName] ?: return null
        val fieldData = field.get(encryptable) as? ByteArray ?: return null
        val storage = getStorageForField(field)
        val refLen = storage.referenceLength
        var referenceBytes = fieldData.takeIf { it.size == refLen }
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)
        if (referenceBytes != null)
            storageFieldIdMap[fieldName] = referenceBytes
        else
            referenceBytes = storageFieldIdMap[fieldName].takeIf { it != null && it.size == refLen }
        return referenceBytes
    }

    /**
     * Returns the individual slice references for a field annotated with [@Sliced].
     *
     * The reference [ByteArray] layout is:
     * ```
     * [0..7]                           → 8 bytes — original plaintext data length (Long)
     * [8 .. 8+refLen-1]                → slice 0 reference
     * [8+refLen .. 8+2*refLen-1]       → slice 1 reference
     * ...
     * ```
     *
     * The returned [SlicedResult.originalLength] allows pre-allocating a [ByteArray] of the exact
     * output size before fetching a single slice — enabling fully parallel fetch + decrypt with
     * direct index placement and no intermediate buffers.
     *
     * Returns null if:
     * - The field is not annotated with [@Sliced]
     * - No reference bytes are found for the field
     * - The reference bytes are too short or corrupted (not a clean multiple of [IStorage.referenceLength])
     *
     * @param encryptable The Encryptable object whose sliced field references are being retrieved.
     * @param fieldName The name of the [@Sliced] field.
     * @return A [SlicedResult] with the original data length and ordered slice references, or null.
     */
    fun getSlices(encryptable: Encryptable<*>, fieldName: String): SlicedResult? {
        val field = getField(encryptable, fieldName)

        // Only proceed if the field is annotated with @Sliced
        if (!field.isAnnotationPresent(Sliced::class.java)) return null

        validateSlicedAnnotation(field, encryptable)

        // First try to get the reference bytes from the field itself, as it may have been cached there by a previous read.
        val fieldData = field.get(encryptable) as? ByteArray ?: return null

        val storage = getStorageForField(field)
        val refLen = storage.referenceLength

        // Retrieve the concatenated reference bytes from the field or the map.
        // Do NOT gate on size == refLen here — sliced references are always 8 + refLen*N bytes,
        // which is larger than refLen for any N >= 1. Size validation happens below.
        val storageFieldIdMap = getStorageFieldIdMap(encryptable)
        val referenceBytes: ByteArray = storageFieldIdMap[fieldName]
            ?: fieldData.takeIf { it.isNotEmpty() }
            ?: run {
                logger.error("Sliced field '$fieldName' in ${encryptable::class.java.name} has no reference bytes available: neither the field nor the storageFieldIdMap contains valid reference data.")
                return null
            }

        // Must have at least the 8-byte length header + one reference
        if (referenceBytes.size < 8 + refLen) {
            logger.error("Sliced field '$fieldName' in ${encryptable::class.java.name} has invalid reference bytes: too short (${referenceBytes.size} bytes).")
            return null
        }

        // Validate that the remaining bytes after the 8-byte header are a clean multiple of referenceLength
        val refsBytes = referenceBytes.size - 8
        if (refsBytes % refLen != 0) {
            logger.error("Sliced field '$fieldName' in ${encryptable::class.java.name} has corrupted reference bytes: $refsBytes bytes is not a multiple of referenceLength $refLen.")
            return null
        }

        // Read the original plaintext data length from the 8-byte header
        // This is not the right place to validade the length of the original ByteArray.
        val originalLength = ByteBuffer.wrap(referenceBytes, 0, 8).long

        val sliceCount = refsBytes / refLen

        // Split the concatenated reference bytes into individual slice references, skipping the 8-byte header
        val references = (0 until sliceCount).map { i ->
            referenceBytes.copyOfRange(8 + i * refLen, 8 + (i + 1) * refLen)
        }

        return SlicedResult(originalLength, references, field)
    }
}