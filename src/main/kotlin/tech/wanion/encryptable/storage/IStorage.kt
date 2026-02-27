package tech.wanion.encryptable.storage

/**
 * Generic storage interface for handling byte data with a reference type.
 *
 * @param R The type of the reference used to identify stored data.
 */
interface IStorage<R: Any> {
    /** Creates a reference from the given byte array, which can be used to retrieve the associated data from storage. */
    fun createReference(referenceBytes: ByteArray?): R?

    /** Retrieves the representation of the reference in ByteArray. */
    fun bytesFromReference(reference: R): ByteArray

    /** Creates a new entry in storage for the given byte array and returns a reference to it. */
    fun create(bytesToStore: ByteArray): R

    /** Creates a new entry in storage for the given byte array and returns the associated byte array representation of the reference. */
    fun createWithBytesReference(bytesToStore: ByteArray): ByteArray = bytesFromReference(create(bytesToStore))

    /** Reads the data associated with the given reference from storage. */
    fun read(reference: R): ByteArray?

    /** Deletes the data associated with the given reference from storage. */
    fun delete(reference: R)
}