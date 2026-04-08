package tech.wanion.encryptable.storage

import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.gridfs.GridFsTemplate
import org.springframework.stereotype.Component
import tech.wanion.encryptable.util.extensions.readFastBytes
import java.io.InputStream

@Component
class GridFSStorage: IStorage<ObjectId> {
    /** The GridFsTemplate used to interact with GridFS for storing and retrieving files. */
    @Autowired
    private lateinit var gridFsTemplate: GridFsTemplate

    /** The expected length of an ObjectId reference in bytes, which is 12 bytes. */
    override val referenceLength: Int = 12

    /**
     * Creates an ObjectId reference from the given byte array if it is a valid ObjectId representation (12 bytes), or returns null if it is not valid.
     *
     * This method is used to convert byte array references stored in the Encryptable's metadata into ObjectId references that can be used to access GridFS.
     * If the provided byte array is exactly 12 bytes long, it is considered a valid ObjectId representation and an ObjectId instance is created and returned.
     * If the byte array is not 12 bytes long, it is considered invalid and null is returned.
     *
     * @param referenceBytes The byte array that may represent an ObjectId reference.
     * @return An ObjectId instance if the byte array is a valid ObjectId representation, or null if it is not valid.
     */
    override fun createReference(referenceBytes: ByteArray?): ObjectId? =
        if (referenceBytes?.size == 12) ObjectId(referenceBytes) else null

    /**
     * Converts the given ObjectId reference into its byte array representation.
     *
     * This method is used to convert ObjectId references into byte arrays that can be stored in the Encryptable's metadata.
     * The ObjectId's byte array representation is obtained using the toByteArray() method, which returns a 12-byte array representing the ObjectId.
     *
     * @param reference The ObjectId reference to be converted into a byte array.
     * @return A byte array representing the given ObjectId reference.
     */
    override fun bytesFromReference(reference: ObjectId): ByteArray = reference.toByteArray()

    /**
     * Creates a new entry in GridFS for the given byte array and returns an ObjectId reference to it.
     *
     * This method is called when an Encryptable object is being saved and its associated large fields need to be stored in GridFS.
     * It creates a new entry in GridFS for the given byte array and returns an ObjectId reference that can be used to retrieve the data later.
     *
     * @param fieldMetadata The metadata string representing the field for which the data is being stored, which can be used to determine the field name or other metadata if needed.
     * @param bytesToStore The byte array containing the data to be stored in GridFS.
     * @return An ObjectId reference that can be used to retrieve the stored data from GridFS later.
     */
    override fun create(fieldMetadata: String, bytesToStore: ByteArray): ObjectId =
        gridFsTemplate.store(bytesToStore.inputStream(), fieldMetadata)

    /**
     * Reads the data associated with the given ObjectId reference from GridFS and returns it as a byte array.
     *
     * This method is called when an Encryptable object is being loaded and its associated large fields need to be retrieved from GridFS.
     * It reads the data associated with the given ObjectId reference from GridFS and returns it as a byte array.
     *
     * @param fieldMetadata The metadata string representing the field for which the data is being read, which can be used to determine the field name or other metadata if needed.
     * @param reference The ObjectId reference whose associated data is being read from GridFS.
     * @return A byte array containing the data associated with the given ObjectId reference, or null if no data is found for that reference.
     */
    override fun read(fieldMetadata: String, reference: ObjectId): ByteArray? {
        var inputStream: InputStream? = null
        try {
            val gridFsFile = gridFsTemplate.findOne(Query(Criteria.where("_id").`is`(reference)))
            inputStream = gridFsTemplate.getResource(gridFsFile).inputStream
            return inputStream.readFastBytes()
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Deletes the data associated with the given ObjectId reference from GridFS.
     *
     * This method is called when an Encryptable object is being deleted and its associated large fields need to be removed from GridFS.
     * It deletes the data associated with the given ObjectId reference from GridFS, ensuring that any stored data for that reference is removed.
     *
     * @param fieldMetadata The metadata string representing the field for which the data is being deleted, which can be used to determine the field name or other metadata if needed.
     * @param reference The ObjectId reference whose associated data is being deleted from GridFS.
     */
    override fun delete(fieldMetadata: String, reference: ObjectId) =
        gridFsTemplate.delete(Query(Criteria.where("_id").`is`(reference)))

    /**
     * Deletes multiple GridFS entries in a single query using an `$in` filter.
     *
     * Unlike the default [IStorage.deleteMany] implementation (which issues one delete per reference in parallel),
     * this override sends a single `{ _id: { $in: [...] } }` query to MongoDB, eliminating all per-file
     * network round-trips and dramatically reducing latency for large batches.
     *
     * @param fieldMetadata The metadata string representing the field for which the data is being deleted.
     * @param references The list of ObjectId references whose associated data is being deleted from GridFS.
     */
    override fun deleteMany(fieldMetadata: String, references: List<ObjectId>) {
        if (references.isEmpty()) return
        gridFsTemplate.delete(Query(Criteria.where("_id").`in`(references)))
    }
}