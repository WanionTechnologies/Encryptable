package tech.wanion.encryptable.storage

import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.extensions.getBean
import tech.wanion.encryptable.util.extensions.readField
import java.lang.reflect.Field
import java.nio.ByteBuffer

/**
 * Represents the result of parsing or constructing a sliced storage reference for a field annotated with [@Sliced].
 *
 * This class encapsulates all metadata needed to reconstruct or operate on a multi-slice field in Encryptable:
 * - The original plaintext length (before slicing)
 * - The ordered list of per-slice storage references (e.g., ObjectId, CID, or other backend key)
 * - The declaring class and field name for traceability and debugging
 *
 * Typical usage:
 * - Returned by [StorageHandler.getSlices] when reading a field's reference bytes from storage
 * - Used to pre-allocate the output array and to fetch/decrypt all slices in parallel
 * - Used for deletion of all slices when a field is cleared or replaced
 *
 * @property originalLength The original length of the plaintext data before slicing. Used to pre-allocate the output array and to determine the size of the last slice.
 * @property slices An ordered list of byte arrays, each containing the storage reference for a slice (e.g., ObjectId, CID, or other backend key) for a corresponding slice. The order matches the original data layout.
 * @property field The [Field] object representing the sliced field. Used to extract class and field names for traceability and debugging.
 * @property className The fully qualified name of the class declaring the sliced field. Useful for logging, debugging, and storage key construction.
 * @property fieldName The name of the field within the declaring class. Used for traceability and storage key construction.
 * @property metadata A combined metadata string in the format "className/fieldName" for easy logging and debugging.
 */
class SlicedResult {
    companion object {
        /** the Storage Handler is needed to determine the reference length for slicing the reference bytes in the constructor. */
        private val storageHandler: StorageHandler = getBean(StorageHandler::class.java)
    }

    /** The original length of the plaintext data before slicing. */
    val originalLength: Long

    /** An ordered list of byte arrays, each containing the storage reference for a corresponding slice. */
    val slices: List<ByteArray>

    /** The [Field] object representing the sliced field. Used to extract class and field names for traceability. */
    val field: Field
    
    /** The fully qualified name of the class declaring the sliced field. */
    val className: String get() = this.field.declaringClass.name

    /** The name of the sliced field within the declaring class. */
    val fieldName: String get() = this.field.name

    /** A combined metadata string in the format "className/fieldName" for easy logging and debugging. */
    val metadata: String get() = "$className/$fieldName"
    
    /**
     * Primary constructor for SlicedResult.
     *
     * @param originalLength The original length of the data before slicing.
     * @param slices The ordered list of per-slice storage references (e.g., ObjectId, CID, or other backend key).
     * @param field The Field object representing the sliced field.
     */
    constructor(originalLength: Long, slices: List<ByteArray>, field: Field) {
        this.originalLength = originalLength
        this.slices = slices
        this.field = field
    }

    /**
     * Constructs a SlicedResult from a raw reference byte array and a field.
     *
     * @param referenceBytes The byte array containing the 8-byte header (original length) followed by concatenated slice references.
     * @param field The Field object representing the sliced field.
     *
     * This constructor parses the header and splits the reference bytes into individual slice references.
     */
    constructor(referenceBytes: ByteArray, field: Field) {
        val storage = storageHandler.getStorageForField(field)
        // get storage reference length for this field.
        val referenceLength = storage.referenceLength

        // must be at least 8 bytes for the original length header plus at least one slice reference.
        if (referenceBytes.size < 8 + referenceLength)
            throw IllegalArgumentException("Reference length for field: ${field.declaringClass.name}.${field.name} must be 8 bytes for the length header plus at least one reference of length: $referenceLength")

        this.originalLength = ByteBuffer.wrap(referenceBytes, 0, 8).long

        val sliceCount = (referenceBytes.size - 8) / referenceLength

        // Split the concatenated reference bytes into individual slice references, skipping the 8-byte header
        this.slices = (0 until sliceCount).map { i ->
            referenceBytes.copyOfRange(8 + i * referenceLength, 8 + (i + 1) * referenceLength)
        }
        
        this.field = field
    }

    /**
     * Constructs a SlicedResult for a given Encryptable entity and field.
     *
     * @param encryptable The Encryptable entity containing the field.
     * @param field The Field object representing the sliced field.
     *
     * This constructor reads the field's reference bytes from the entity and delegates to the referenceBytes constructor.
     */
    constructor(encryptable: Encryptable<*>, field: Field): this(encryptable.readField<ByteArray>(field.name), field)
}