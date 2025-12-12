package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_hkdf_entities")
class TestHKDFEntity : Encryptable<TestHKDFEntity>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var sensitiveData: String? = null
}

@Repository
interface TestHKDFEntityRepository : EncryptableMongoRepository<TestHKDFEntity>