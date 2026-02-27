package tech.wanion.encryptable.storage

import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.gridfs.GridFsTemplate
import org.springframework.stereotype.Component
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.readFastBytes
import java.io.InputStream

@Component
class GridFSStorage: IStorage<ObjectId> {
    private val gridFsTemplate: GridFsTemplate = getBean(GridFsTemplate::class.java)

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
     * Creates a new entry in GridFS for the specified byte array data and returns the ObjectId reference for that data.
     *
     * This method is called when a byte array field is updated from a small value (stored inline) to a large value (stored in GridFS),
     * or when a new Encryptable object is created with a large byte array field that exceeds the inline storage threshold,
     * and the storage system needs to create a new entry in GridFS for the field data.
     * It stores the byte array data in GridFS and returns the ObjectId reference associated with that data.
     *
     * @param bytesToStore The byte array data to be stored in GridFS.
     * @return The ObjectId reference associated with the stored byte array data.
     */
    override fun create(bytesToStore: ByteArray): ObjectId =
        gridFsTemplate.store(bytesToStore.inputStream(), "gridFsField")

    /**
     * Reads the data associated with the specified ObjectId reference from GridFS and returns it as a byte array.
     *
     * This method is called when a specific ObjectId reference needs to be read from GridFS, such as when an Encryptable object is being loaded and its associated large fields need to be retrieved,
     * or when a specific ObjectId reference needs to be accessed for any reason.
     * It retrieves the data associated with the ObjectId reference from GridFS and returns it as a byte array.
     *
     * @param reference The ObjectId reference whose associated data is being read from GridFS.
     * @return The byte array containing the data associated with the specified ObjectId reference, or null if no data is found.
     */
    override fun read(reference: ObjectId): ByteArray? {
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
     * Deletes the data associated with the specified ObjectId reference from GridFS.
     * This method is called when an Encryptable object is deleted and its associated large fields need to be cleaned up,
     * or when a specific ObjectId reference needs to be removed from GridFS for any reason.
     * It removes the data associated with the ObjectId reference from GridFS.
     *
     * @param reference The ObjectId reference whose associated data is being deleted from GridFS.
     */
    override fun delete(reference: ObjectId) =
        gridFsTemplate.delete(Query(Criteria.where("_id").`is`(reference)))
}