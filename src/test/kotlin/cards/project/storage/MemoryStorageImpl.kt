package cards.project.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tech.wanion.encryptable.CID
import tech.wanion.encryptable.CID.Companion.cid
import tech.wanion.encryptable.storage.IStorage
import tech.wanion.encryptable.util.extensions.first4KBChecksum
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of the IStorage interface that uses in-memory storage for byte arrays associated with CIDs.
 *
 * be aware that this implementation is not suitable for production use due to potential memory leaks and lack of persistence.
 * It is intended for testing and demonstration purposes only.
 */
@Component
class MemoryStorageImpl: IStorage<CID> {
    /** Logger for debugging and informational messages. */
    val logger = LoggerFactory.getLogger(MemoryStorageImpl::class.java)

    /** In-memory storage using a concurrent hash map to store byte arrays associated with their corresponding CIDs. */
    private val memoryStorage = ConcurrentHashMap<CID, ByteArray>()

    /** The expected length of a CID reference in bytes, which is 16 bytes. */
    override val referenceLength: Int = 16

    /** Creates a reference (CID) from the given bytes, or returns null if the input is null. */
    override fun createReference(referenceBytes: ByteArray?): CID? = referenceBytes?.cid

    /** Retrieves the bytes associated with the given reference (CID). */
    override fun bytesFromReference(reference: CID): ByteArray = reference.bytes

    /** Stores the given bytes and returns a reference (CID) to it. */
    override fun create(fieldMetadata: String, bytesToStore: ByteArray): CID {
        val reference = CID.random()
        logger.info("Creating ${bytesToStore.first4KBChecksum()} with Reference: $reference.")
        memoryStorage[reference] = bytesToStore
        return reference
    }

    /** For testing purposes, we can add a method to read the stored bytes by reference */
    override fun read(fieldMetadata: String, reference: CID): ByteArray? {
        val bytes = memoryStorage[reference]
        logger.info("Read ${bytes?.first4KBChecksum()}")
        return bytes
    }

    /** For testing purposes, we can add a method to clear the storage */
    override fun delete(fieldMetadata: String, reference: CID) {
        logger.info("Deleting Reference: $reference.")
        memoryStorage.remove(reference)
    }
}