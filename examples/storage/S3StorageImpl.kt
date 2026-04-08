package com.example

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.CID.Companion.cid
import tech.wanion.encryptable.storage.IStorage
import tech.wanion.encryptable.util.Limited.parallelForEach
import java.io.ByteArrayOutputStream

/**
 * S3StorageImpl is a working example implementation of IStorage<CID> for storing and retrieving data in an S3-compatible bucket.
 *
 * This class demonstrates how simple it is to integrate different storage backends with Encryptable.
 * By implementing the IStorage interface and using a straightforward key structure, developers can quickly add support for new storage systems such as Amazon S3 or compatible services.
 *
 * Instead of using Java reflection, this implementation uses a String parameter called 'fieldMetadata' to organize and identify stored objects.
 * The 'fieldMetadata' string should contain the fully qualified class name and field name, separated by a slash, in the format:
 *
 *     <ClassName>/<fieldName>/<key>
 *
 * For example, storing an object for the field 'profilePicture' in class 'UserEntity' will result in a key like:
 *     com.example.domain.UserEntity/profilePicture/abc123cid
 *
 * This approach enables metadata extraction, traceability, and management of stored objects, as the key itself encodes
 * the context of the object. The regex 'keyMetadataRegex' is used to extract this metadata from the key when needed.
 *
 * S3 does not have true folders, but this key structure creates virtual folders in the bucket, allowing objects to be grouped
 * and browsed as if they were organized in directories by class and field name.
 *
 * If the 'fieldMetadata' parameter is omitted or not properly formatted, objects are not grouped by class and field, and this organization is lost.
 *
 * All create, read, and delete operations use the 'fieldMetadata' string to construct the S3 key, ensuring consistent organization and enabling metadata-driven operations.
 */
@Component
class S3StorageImpl : IStorage<CID> {
    @Autowired
    /** The S3 client used to interact with the S3 storage service. */
    lateinit var s3Client: S3Client

    /** Regex to extract class, field, and key from S3 object keys. */
    private val metadataKeyRegex = Regex("(?<class>[^/]+)/(?<field>[^/]+)(?:/(?<key>.+))?")

    /** The fixed length of the byte array representation of a CID reference. */
    override val referenceLength: Int = 16

    /** Replace with your bucket name or inject via @Value. */
    val bucketName: String = "your-bucket-name"

    /** Creates a CID reference from the given byte array if it is 16 bytes long, otherwise returns null. */
    override fun createReference(referenceBytes: ByteArray?): CID? =
        if (referenceBytes?.size == 16) referenceBytes.cid else null

    /** Retrieves the byte array representation of the given CID reference. */
    override fun bytesFromReference(reference: CID): ByteArray = reference.bytes

    /**
     * Creates a new entry in S3 storage for the given byte array and returns a CID reference to it.
     *
     * @param fieldMetadata A string containing the fully qualified class name and field name, separated by a slash (e.g., "com.example.domain.UserEntity/profilePicture").
     * @param bytesToStore The byte array to be stored in S3.
     * @return A randomly generated CID reference for the stored object.
     *
     * The S3 key is constructed as <fieldMetadata>/<key>, enabling metadata-driven organization and traceability.
     */
    override fun create(fieldMetadata: String, bytesToStore: ByteArray): CID {
        val cid = CID.random()
        val key = cid.toBase64Url()
        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key("$fieldMetadata/$key")
            .build()
        s3Client.putObject(putRequest, RequestBody.fromBytes(bytesToStore))
        return cid
    }

    /**
     * Reads the object associated with the given CID reference from S3 storage and returns it as a byte array.
     *
     * @param fieldMetadata A string containing the fully qualified class name and field name, separated by a slash (e.g., "com.example.domain.UserEntity/profilePicture").
     * @param reference The CID reference of the object to retrieve.
     * @return The byte array of the stored object, or null if not found.
     *
     * The S3 key is reconstructed as <fieldMetadata>/<key> for retrieval.
     */
    override fun read(fieldMetadata: String, reference: CID): ByteArray? {
        val key = reference.toBase64Url()
        val getRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key("$fieldMetadata/$key")
            .build()
        s3Client.getObject(getRequest).use { inputStream ->
            val buffer = ByteArrayOutputStream()
            inputStream.copyTo(buffer)
            return buffer.toByteArray()
        }
    }

    /**
     * Deletes the object associated with the given CID reference from S3 storage.
     *
     * @param fieldMetadata A string containing the fully qualified class name and field name, separated by a slash (e.g., "com.example.domain.UserEntity/profilePicture").
     * @param reference The CID reference of the object to delete.
     *
     * The S3 key is reconstructed as <fieldMetadata>/<key> for deletion.
     */
    override fun delete(fieldMetadata: String, reference: CID) {
        val key = reference.toBase64Url()
        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key("$fieldMetadata/$key")
            .build()
        s3Client.deleteObject(deleteRequest)
    }

    /**
     * Deletes multiple objects associated with the given CID references from S3 storage using S3's native batch delete API.
     *
     * S3's `DeleteObjects` API accepts a maximum of **1000 keys per request**. This method automatically
     * chunks the references into batches of 1000, issuing one `DeleteObjects` call per chunk. For typical
     * Encryptable use cases (even large batch deletes with many `@Sliced` fields), this keeps the total
     * number of network round-trips to a minimum — O(references / 1000) instead of O(references).
     * All chunks are issued in parallel (virtual threads), so even datasets exceeding 1000 references
     * incur only a single network latency penalty regardless of how many chunks are needed.
     *
     * The `quiet(true)` flag suppresses per-object success responses from S3, reducing response payload size.
     * Errors are still reported by S3 if any individual key fails to delete.
     *
     * @param fieldMetadata A string containing the fully qualified class name and field name, separated by a slash (e.g., "com.example.domain.UserEntity/profilePicture").
     * @param references The list of CID references of the objects to delete.
     *
     * If the references list is empty, no action is taken.
     */
    override fun deleteMany(fieldMetadata: String, references: List<CID>) {
        if (references.isEmpty()) return

        // S3 DeleteObjects API hard limit: 1000 keys per request.
        // Chunks are dispatched in parallel — all round-trips happen concurrently.
        references.chunked(1000).parallelForEach { chunk ->
            val objectIdentifiers = chunk.map { cid ->
                ObjectIdentifier.builder()
                    .key("$fieldMetadata/${cid.toBase64Url()}")
                    .build()
            }
            val deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(
                    Delete.builder()
                        .objects(objectIdentifiers)
                        .quiet(true) // suppress per-object success responses; errors are still reported.
                        .build()
                )
                .build()
            s3Client.deleteObjects(deleteRequest)
        }
    }
}