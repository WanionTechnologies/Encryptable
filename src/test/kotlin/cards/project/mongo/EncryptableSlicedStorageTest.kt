package cards.project.mongo

import cards.project.mongo.entity.TestSlicedEntity
import cards.project.mongo.entity.TestSlicedEntityRepository
import cards.project.mongo.entity.TestSlicedIdEntity
import cards.project.mongo.entity.TestSlicedIdEntityRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tech.wanion.encryptable.MasterSecretHolder
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.extensions.metadata

/**
 * # Sliced Storage Tests
 *
 * Verifies the full lifecycle of [@Sliced] ByteArray fields: save, round-trip, key correctness,
 * per-slice encryption, update (atomic replace), and delete (all slices removed from storage).
 *
 * ## Why these tests matter
 *
 * [@Sliced] fields use a fundamentally different storage layout than regular fields:
 * - The entity document holds a concatenated reference [ByteArray] with a 4-byte length header
 *   followed by N × `referenceLength` slice references, instead of a single reference.
 * - Each slice is independently encrypted with its own random IV (AES-256-GCM).
 * - On read, all slices must be fetched in parallel and reassembled in the correct order.
 * - On update, new slices must be stored before old ones are deleted (atomic replace).
 * - On delete, all N slice references must be removed — not just one.
 *
 * A bug in any of these steps would produce either corrupted data, orphaned storage entries,
 * or silently wrong plaintext — none of which a standard round-trip test would catch if the
 * same bug is present in both the write and read paths.
 *
 * ## What these tests do
 *
 * ### Round-trip and reassembly
 * Save a known payload, reload it, assert byte-for-byte equality. This catches any
 * slice boundary off-by-one, wrong slice order, or truncated last slice.
 *
 * ### Multi-slice boundary
 * Use a payload whose size is not a clean multiple of the slice size, so the last slice is
 * shorter than `sizeMB` MB. This verifies that the 4-byte length header is correctly used
 * to trim the last slice on reassembly instead of returning zero-padded garbage.
 *
 * ### Key correctness (bypasses the framework decrypt path)
 * After save, pull each slice's raw ciphertext directly from [StorageHandler] and manually
 * call [AES256.decrypt] with:
 *   1. The **correct** key — must yield the corresponding plaintext slice.
 *   2. The **wrong** key — must return the encrypted payload unchanged (AES-GCM auth failure).
 * This is the same technique used by [EncryptableKeyCorrectnessTest] for non-sliced fields.
 *
 * ### Unencrypted sliced field
 * Verifies that [@Sliced] works without [@Encrypt]: slices are stored and reassembled as raw
 * bytes, with no encryption applied. A decrypt attempt on the stored bytes must fail.
 *
 * ### Update — atomic replace
 * Replace the field value and verify:
 *   - The new value round-trips correctly.
 *   - The old slices are gone from storage (no orphans).
 *   - The new slices are present.
 *
 * ### Delete — all slices removed
 * Delete the entity and verify that every slice reference was removed from the storage backend.
 *
 * ### @Id entity — master secret used for slices
 * Mirrors the @HKDFId key-correctness test, but for [@Id] entities where the correct
 * key is the master secret — not the CID.
 */
class EncryptableSlicedStorageTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var slicedRepository: TestSlicedEntityRepository

    @Autowired
    private lateinit var slicedIdRepository: TestSlicedIdEntityRepository

    @Autowired
    private lateinit var storageHandler: StorageHandler

    /**
     * Returns the slice size in bytes for TestSlicedEntity's slicedContent field (sizeMB = 2).
     * Used to compute expected slice count and to locate individual slice boundaries.
     */
    private val sliceSizeBytes = 2 * 1024 * 1024

    // -------------------------------------------------------------------------
    // Round-trip — payload is an exact multiple of slice size
    // -------------------------------------------------------------------------

    @Test
    fun `round-trip - exact multiple of slice size`() {
        // Given — 4 MB = exactly 2 slices of 2 MB each
        val secret = generateSecret()
        val original = createSampleBytes(4 * 1024) // createSampleBytes takes KB
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "exact-multiple"
            slicedContent = original
        }

        // When
        slicedRepository.save(entity)
        val retrieved = slicedRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved, "Entity must be retrievable")
        assertArrayEquals(original, retrieved!!.slicedContent, "Reassembled payload must equal original")

        // Cleanup
        slicedRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // Round-trip — last slice is shorter than slice size (non-multiple payload)
    // -------------------------------------------------------------------------

    @Test
    fun `round-trip - non-multiple payload, last slice is shorter`() {
        // Given — 5 MB = 2 full slices (2 MB each) + 1 partial slice (1 MB)
        val secret = generateSecret()
        val original = createSampleBytes(5 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "non-multiple"
            slicedContent = original
        }

        // When
        slicedRepository.save(entity)
        val retrieved = slicedRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals(original.size, retrieved!!.slicedContent!!.size, "Reassembled size must match original — last slice must not be zero-padded")
        assertArrayEquals(original, retrieved.slicedContent, "Reassembled payload must equal original")

        // Cleanup
        slicedRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // Key correctness — each slice is encrypted with the entity secret (@HKDFId)
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId - each slice is encrypted with entity secret, not master secret`() {
        // Given — 6 MB = 3 slices of 2 MB each
        val secret = generateSecret()
        val original = createSampleBytes(6 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "key-correctness-hkdf"
            slicedContent = original
        }

        slicedRepository.save(entity)

        // Pull the slice references by bypassing the framework's read path entirely.
        val slicedResult = storageHandler.getSlices(entity, "slicedContent")
        assertNotNull(slicedResult, "getSlices must return a SlicedResult after save")

        val storage = storageHandler.getStorageForField(TestSlicedEntity::class.java, "slicedContent")
        val field = storageHandler.getField(TestSlicedEntity::class.java, "slicedContent")

        assertEquals(3, slicedResult!!.references.size, "6 MB payload with 2 MB slices must produce exactly 3 slices")

        val masterSecret = MasterSecretHolder.getMasterSecret()

        slicedResult.references.forEachIndexed { i, refBytes ->
            val reference = storage.createReference(refBytes)
            assertNotNull(reference, "Reference for slice $i must be valid")

            // Read the raw ciphertext directly from the storage backend — no framework decrypt.
            val rawCiphertext = storage.read(field.metadata, reference!!)
            assertNotNull(rawCiphertext, "Storage must return bytes for slice $i")
            rawCiphertext as ByteArray

            // The expected plaintext for this slice
            val from = i * sliceSizeBytes
            val to = minOf(from + sliceSizeBytes, original.size)
            val expectedPlaintext = original.copyOfRange(from, to)

            // (1) Correct key — entity secret — must yield the expected plaintext
            val decryptedCorrect = AES256.decrypt(secret, TestSlicedEntity::class.java, rawCiphertext)
            assertArrayEquals(
                expectedPlaintext,
                decryptedCorrect,
                "Slice $i must decrypt correctly with the entity secret"
            )

            // (2) Wrong key — master secret — must return the encrypted payload unchanged
            val decryptedWrong = AES256.decrypt(masterSecret, TestSlicedEntity::class.java, rawCiphertext)
            assertTrue(
                decryptedWrong.contentEquals(rawCiphertext),
                "Slice $i must NOT decrypt with the master secret — AES-GCM auth must fail and return payload unchanged"
            )
        }

        // Cleanup
        slicedRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // Key correctness — @Id entity slices use master secret, not the CID
    // -------------------------------------------------------------------------

    @Test
    fun `@Id - each slice is encrypted with master secret, not CID`() {
        // Given — 4 MB = 2 slices
        val cid = CID.randomCIDString()
        val original = createSampleBytes(4 * 1024)
        val entity = TestSlicedIdEntity().withSecret(cid).apply {
            label = "key-correctness-id"
            slicedContent = original
        }

        slicedIdRepository.save(entity)

        val slicedResult = storageHandler.getSlices(entity, "slicedContent")
        assertNotNull(slicedResult)

        val storage = storageHandler.getStorageForField(TestSlicedIdEntity::class.java, "slicedContent")
        val field = storageHandler.getField(TestSlicedIdEntity::class.java, "slicedContent")
        val masterSecret = MasterSecretHolder.getMasterSecret()

        assertEquals(2, slicedResult!!.references.size, "4 MB payload with 2 MB slices must produce exactly 2 slices")

        slicedResult.references.forEachIndexed { i, refBytes ->
            val reference = storage.createReference(refBytes)
            assertNotNull(reference)

            val rawCiphertext = storage.read(field.metadata, reference!!)
            assertNotNull(rawCiphertext)
            rawCiphertext as ByteArray

            val from = i * sliceSizeBytes
            val to = minOf(from + sliceSizeBytes, original.size)
            val expectedPlaintext = original.copyOfRange(from, to)

            // (1) Correct key — master secret
            val decryptedCorrect = AES256.decrypt(masterSecret, TestSlicedIdEntity::class.java, rawCiphertext)
            assertArrayEquals(
                expectedPlaintext,
                decryptedCorrect,
                "@Id slice $i must decrypt correctly with the master secret"
            )

            // (2) Wrong key — CID — must fail (regression check for the @Id key bug pattern)
            val decryptedWrong = AES256.decrypt(cid, TestSlicedIdEntity::class.java, rawCiphertext)
            assertTrue(
                decryptedWrong.contentEquals(rawCiphertext),
                "@Id slice $i must NOT decrypt with the CID — regression check for wrong-key pattern"
            )
        }

        slicedIdRepository.deleteBySecret(cid)
    }

    // -------------------------------------------------------------------------
    // Unencrypted sliced field — @Sliced without @Encrypt
    // -------------------------------------------------------------------------

    @Test
    fun `unencrypted @Sliced field - slices are stored and reassembled as raw bytes`() {
        // Given — 4 MB, no @Encrypt on slicedPublicContent
        val secret = generateSecret()
        val original = createSampleBytes(4 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "unencrypted-sliced"
            slicedPublicContent = original
        }

        slicedRepository.save(entity)
        val retrieved = slicedRepository.findBySecretOrNull(secret)

        assertNotNull(retrieved)
        assertArrayEquals(original, retrieved!!.slicedPublicContent, "Unencrypted sliced field must round-trip correctly")

        // Verify the raw bytes in storage are actually the plaintext (no encryption applied).
        val slicedResult = storageHandler.getSlices(entity, "slicedPublicContent")
        assertNotNull(slicedResult)

        val storage = storageHandler.getStorageForField(TestSlicedEntity::class.java, "slicedPublicContent")
        val field = storageHandler.getField(TestSlicedEntity::class.java, "slicedPublicContent")

        slicedResult!!.references.forEachIndexed { i, refBytes ->
            val reference = storage.createReference(refBytes)!!
            val storedBytes = storage.read(field.metadata, reference)!!

            val from = i * sliceSizeBytes
            val to = minOf(from + sliceSizeBytes, original.size)
            val expectedPlaintext = original.copyOfRange(from, to)

            // Without @Encrypt, what is stored must be the raw plaintext
            assertArrayEquals(
                expectedPlaintext,
                storedBytes,
                "Slice $i of an unencrypted field must be stored as raw plaintext"
            )
        }

        slicedRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // Update — atomic replace: new slices stored, old slices removed
    // -------------------------------------------------------------------------

    @Test
    fun `update - new slices stored atomically, old slices orphan-free`() {
        // Given — initial save with 4 MB
        val secret = generateSecret()
        val original = createSampleBytes(4 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "atomic-replace"
            slicedContent = original
        }
        slicedRepository.save(entity)

        // Capture the old slice references before the update
        val oldSlicedResult = storageHandler.getSlices(entity, "slicedContent")
        assertNotNull(oldSlicedResult)
        val storage = storageHandler.getStorageForField(TestSlicedEntity::class.java, "slicedContent")
        val field = storageHandler.getField(TestSlicedEntity::class.java, "slicedContent")

        val oldReferences = oldSlicedResult!!.references.map { refBytes ->
            storage.createReference(refBytes)!!
        }

        // When — update with a different 6 MB payload (3 slices)
        val updated = slicedRepository.findBySecretOrNull(secret)!!
        val newPayload = createSampleBytes(6 * 1024)
        updated.slicedContent = newPayload
        // Flush dirty tracking so afterCompletion persists the change before the reload.
        slicedRepository.flushThenClear()
        val reloaded = slicedRepository.findBySecretOrNull(secret)!!

        // Then — new payload round-trips correctly
        assertArrayEquals(newPayload, reloaded.slicedContent, "Updated payload must round-trip correctly")

        // Then — old slices must be gone from storage (no orphans)
        oldReferences.forEachIndexed { i, ref ->
            assertNull(
                storage.read(field.metadata, ref),
                "Old slice $i must be deleted from storage after update (atomic replace)"
            )
        }

        // Cleanup
        slicedRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // Delete — all slices removed from storage
    // -------------------------------------------------------------------------

    @Test
    fun `delete - all slice references are removed from storage`() {
        // Given — 6 MB = 3 slices
        val secret = generateSecret()
        val original = createSampleBytes(6 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "delete-all-slices"
            slicedContent = original
        }
        slicedRepository.save(entity)

        // Capture all slice references before deletion
        val slicedResult = storageHandler.getSlices(entity, "slicedContent")
        assertNotNull(slicedResult)
        val storage = storageHandler.getStorageForField(TestSlicedEntity::class.java, "slicedContent")
        val field = storageHandler.getField(TestSlicedEntity::class.java, "slicedContent")

        val references = slicedResult!!.references.map { refBytes ->
            storage.createReference(refBytes)!!
        }

        assertEquals(3, references.size, "Must have 3 slices before deletion")

        // When
        slicedRepository.deleteBySecret(secret)

        // Then — entity is gone
        assertNull(slicedRepository.findBySecretOrNull(secret), "Entity must not exist after deletion")

        // Then — every slice must be gone from storage
        references.forEachIndexed { i, ref ->
            assertNull(
                storage.read(field.metadata, ref),
                "Slice $i must be deleted from storage when the entity is deleted"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Reference header — slice count and original length are correct
    // -------------------------------------------------------------------------

    @Test
    fun `reference header - originalLength and slice count are correct`() {
        // Given — 5 MB = 2 full + 1 partial slice
        val secret = generateSecret()
        val original = createSampleBytes(5 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "reference-header"
            slicedContent = original
        }
        slicedRepository.save(entity)

        val slicedResult = storageHandler.getSlices(entity, "slicedContent")
        assertNotNull(slicedResult)

        // originalLength must exactly match the payload size
        assertEquals(
            original.size,
            slicedResult!!.originalLength,
            "originalLength in the reference header must equal the original plaintext size"
        )

        // 5 MB / 2 MB = ceiling(2.5) = 3 slices
        assertEquals(3, slicedResult.references.size, "5 MB with 2 MB slices must produce 3 slices")

        slicedRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // Null assignment — sliced field can be cleared, slices are deleted
    // -------------------------------------------------------------------------

    @Test
    fun `null assignment - clearing sliced field removes all slices from storage`() {
        // Given
        val secret = generateSecret()
        val original = createSampleBytes(4 * 1024)
        val entity = TestSlicedEntity().withSecret(secret).apply {
            name = "null-clear"
            slicedContent = original
        }
        slicedRepository.save(entity)

        val slicedResult = storageHandler.getSlices(entity, "slicedContent")
        assertNotNull(slicedResult)
        val storage = storageHandler.getStorageForField(TestSlicedEntity::class.java, "slicedContent")
        val field = storageHandler.getField(TestSlicedEntity::class.java, "slicedContent")
        val references = slicedResult!!.references.map { storage.createReference(it)!! }

        // When — reload, clear the field, flush, then reload again to confirm persistence.
        val loaded = slicedRepository.findBySecretOrNull(secret)!!
        loaded.slicedContent = null
        // Flush dirty tracking so afterCompletion persists the null before the reload.
        slicedRepository.flushThenClear()
        val reloaded = slicedRepository.findBySecretOrNull(secret)!!

        // Then — field is null after reload
        assertNull(reloaded.slicedContent, "slicedContent must be null after being cleared")

        // Then — all old slices are gone from storage
        references.forEachIndexed { i, ref ->
            assertNull(
                storage.read(field.metadata, ref),
                "Slice $i must be deleted from storage when the field is set to null"
            )
        }

        slicedRepository.deleteBySecret(secret)
    }
}

