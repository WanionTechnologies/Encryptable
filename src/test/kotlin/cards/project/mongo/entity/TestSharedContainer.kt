package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.mongo.HKDFId

@Document(collection = "test_shared_containers")
class TestSharedContainer : Encryptable<TestSharedContainer>() {
    @HKDFId
    override var id: CID? = null

    var name: String? = null

    // List WITHOUT @PartOf - items are shared references
    var sharedItems: MutableList<TestItem> = mutableListOf()
}

@Repository
interface TestSharedContainerRepository : EncryptableMongoRepository<TestSharedContainer>