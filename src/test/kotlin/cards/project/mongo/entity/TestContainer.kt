package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_containers")
class TestContainer : Encryptable<TestContainer>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var title: String? = null

    @PartOf
    var items: MutableList<TestItem> = mutableListOf()
}

@Repository
interface TestContainerRepository : EncryptableMongoRepository<TestContainer>