package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_items")
class TestItem : Encryptable<TestItem>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var name: String? = null

    @Encrypt
    var value: Int? = null
}

@Repository
interface TestItemRepository : EncryptableMongoRepository<TestItem>