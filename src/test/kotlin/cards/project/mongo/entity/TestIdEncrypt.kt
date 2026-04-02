package cards.project.mongo.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.CID
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.mongo.PartOf

/**
 * Test entity using the @Id strategy with an @Encrypt ByteArray field and an @Encrypt String field.
 *
 * Used exclusively by EncryptableKeyCorrectnessTest to verify that the correct
 * encryption key (master secret) is used for @Id entities — not the CID.
 */
@Document(collection = "test_id_encrypt_entities")
class TestIdEncryptEntity : Encryptable<TestIdEncryptEntity>() {
    @Id
    override var id: CID? = null

    @Encrypt
    var sensitiveBytes: ByteArray? = null

    @Encrypt
    var sensitiveLabel: String? = null

    @Encrypt
    var sensitiveList: MutableList<String> = mutableListOf()

    var publicLabel: String? = null
}

@Repository
interface TestIdEncryptEntityRepository : EncryptableMongoRepository<TestIdEncryptEntity>

/**
 * Test entity using the @Id strategy with @PartOf nested Encryptable fields.
 *
 * Used by EncryptableKeyCorrectnessTest to verify that encryptableFieldMap and
 * encryptableListFieldMap store the nested entity's ID as a plaintext reference
 * for @Id parents (isolated=false) — values are never encrypted.
 */
@Document(collection = "test_id_nested_entities")
class TestIdNestedEntity : Encryptable<TestIdNestedEntity>() {
    @Id
    override var id: CID? = null

    @Encrypt
    var label: String? = null

    @PartOf
    var nestedAddress: TestAddress? = null

    @PartOf
    var nestedItems: MutableList<TestItem> = mutableListOf()
}

@Repository
interface TestIdNestedEntityRepository : EncryptableMongoRepository<TestIdNestedEntity>
