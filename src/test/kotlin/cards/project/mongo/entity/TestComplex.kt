package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_complex_entities")
class TestComplexEntity : Encryptable<TestComplexEntity>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var stringField: String? = null

    @Encrypt
    var stringList: MutableList<String> = mutableListOf()

    @Encrypt
    var binaryField: ByteArray? = null

    var publicField: String? = null
    var numberField: Int = 0
}

@Repository
interface TestComplexEntityRepository : EncryptableMongoRepository<TestComplexEntity>