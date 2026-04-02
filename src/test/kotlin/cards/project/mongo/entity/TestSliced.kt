package cards.project.mongo.entity

import cards.project.storage.MemoryStorage
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.CID
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.mongo.HKDFId
import tech.wanion.encryptable.storage.Sliced

/**
 * Test entity using the @HKDFId strategy with a @Sliced @MemoryStorage @Encrypt ByteArray field.
 *
 * Used by EncryptableSlicedStorageTest to verify:
 *   - Sliced fields are correctly split, stored, and reassembled
 *   - Each slice is individually encrypted with the entity secret
 *   - The concatenated reference header is persisted correctly
 *   - Update and delete operations handle all slices atomically
 */
@Document(collection = "test_sliced_entities")
class TestSlicedEntity : Encryptable<TestSlicedEntity>() {
    @HKDFId
    override var id: CID? = null

    var name: String? = null

    /** 2 MB slices — small enough to produce multiple slices with the test payloads used in tests. */
    @Encrypt
    @MemoryStorage
    @Sliced(sizeMB = 2)
    var slicedContent: ByteArray? = null

    /** Unencrypted sliced field — verifies that slicing works independently of @Encrypt. */
    @MemoryStorage
    @Sliced(sizeMB = 2)
    var slicedPublicContent: ByteArray? = null
}

@Repository
interface TestSlicedEntityRepository : EncryptableMongoRepository<TestSlicedEntity>

// ---------------------------------------------------------------------------

/**
 * Test entity using the @Id strategy with a @Sliced @MemoryStorage @Encrypt ByteArray field.
 *
 * Used by EncryptableSlicedStorageTest to verify that the master secret is used for sliced @Id
 * entities — not the CID — mirroring the key-correctness check done for non-sliced fields.
 */
@Document(collection = "test_sliced_id_entities")
class TestSlicedIdEntity : Encryptable<TestSlicedIdEntity>() {
    @Id
    override var id: CID? = null

    var label: String? = null

    @Encrypt
    @MemoryStorage
    @Sliced(sizeMB = 2)
    var slicedContent: ByteArray? = null
}

@Repository
interface TestSlicedIdEntityRepository : EncryptableMongoRepository<TestSlicedIdEntity>

