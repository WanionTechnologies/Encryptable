package tech.wanion.encryptable.storage

import tech.wanion.encryptable.util.Limited.parallelForEach

/**
 * Generic storage interface for handling byte data with a reference type.
 *
 * @param R The type of the reference used to identify stored data.
 */
interface IStorage<R: Any> {
    /** Returns the expected length of the reference in bytes, which is used to validate and create references from byte arrays. */
    val referenceLength: Int

    /** Creates a reference from the given byte array, which can be used to retrieve the associated data from storage. */
    fun createReference(referenceBytes: ByteArray?): R?

    /** Creates references from a list of byte arrays. */
    fun createReferences(referencesBytesList: List<ByteArray?>): List<R> = referencesBytesList.map { createReference(it)!! }

    /** Retrieves the representation of the reference in ByteArray. */
    fun bytesFromReference(reference: R): ByteArray

    /** Creates a new entry in storage for the given byte array and returns a reference to it. */
    fun create(fieldMetadata: String, bytesToStore: ByteArray): R

    /** Creates a new entry in storage for the given byte array and returns the associated byte array representation of the reference. */
    fun createWithBytesReference(fieldMetadata: String, bytesToStore: ByteArray): ByteArray = bytesFromReference(create(fieldMetadata, bytesToStore))

    /** Reads the data associated with the given reference from storage and returns it as a byte array. */
    fun read(fieldMetadata: String, reference: R): ByteArray?

    /** Deletes the data associated with the given reference from storage. */
    fun delete(fieldMetadata: String, reference: R)

    /**
     * Deletes multiple entries in storage for the given list of references.
     *
     * Default implementation: Executes [delete] for each reference in parallel, which may result in
     * multiple individual delete operations. This is functional but not optimal for large batches.
     *
     * **Override this method** for better performance by implementing batch deletions specific to
     * your storage backend (e.g., bulk S3 delete.).
     * This avoids network round-trips, improves atomicity, and reduces latency significantly.
     *
     * @param fieldMetadata metadata describing the field being deleted from
     * @param references list of references to delete from storage
     */
    fun deleteMany(fieldMetadata: String, references: List<R>) {
        references.parallelForEach { delete(fieldMetadata, it) }
    }
}