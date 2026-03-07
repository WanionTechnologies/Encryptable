package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

/**
 * @HKDFId entity with @SimpleReference nested Encryptable fields.
 *
 * Used by EncryptableKeyCorrectnessTest to verify that @SimpleReference on an
 * @HKDFId parent stores the nested entity's ID as plaintext — not the secret,
 * and not encrypted — even though the parent is isolated.
 */
@Document(collection = "test_simple_ref_entities")
class TestSimpleRefEntity : Encryptable<TestSimpleRefEntity>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var name: String? = null

    @PartOf
    @SimpleReference
    var simpleChild: TestAddress? = null

    @PartOf
    @SimpleReference
    var simpleChildren: MutableList<TestItem> = mutableListOf()
}

@Repository
interface TestSimpleRefEntityRepository : EncryptableMongoRepository<TestSimpleRefEntity>

