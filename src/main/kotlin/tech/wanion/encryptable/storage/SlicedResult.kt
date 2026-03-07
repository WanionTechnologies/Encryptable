package tech.wanion.encryptable.storage

import java.lang.reflect.Field

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
 * @property references An ordered list of byte arrays, each containing the storage reference (e.g., ObjectId, CID, or other backend key) for a corresponding slice. The order matches the original data layout.
 * @property className The fully qualified name of the class declaring the sliced field. Useful for logging, debugging, and storage key construction.
 * @property fieldName The name of the field within the declaring class. Used for traceability and storage key construction.
 * @property metadata A combined metadata string in the format "className/fieldName" for easy logging and debugging.
 */
class SlicedResult(
    /** The original length of the plaintext data before slicing. */
    val originalLength: Int,
    /** An ordered list of byte arrays, each containing the storage reference for a corresponding slice. */
    val references: List<ByteArray>,
    /** The [Field] object representing the sliced field. Used to extract class and field names for traceability. */
    field: Field
) {
    /** The fully qualified name of the class declaring the sliced field. */
    val className: String = field.declaringClass.name

    /** The name of the sliced field within the declaring class. */
    val fieldName: String = field.name

    /** A combined metadata string in the format "className/fieldName" for easy logging and debugging. */
    val metadata: String get() = "$className/$fieldName"
}