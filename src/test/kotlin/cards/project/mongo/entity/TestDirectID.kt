package cards.project.mongo.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository

@Document(collection = "test_direct_id_entities")
class TestDirectIDEntity : Encryptable<TestDirectIDEntity>() {
    @Id
    override var id: CID? = null

    // Note: @Encrypt won't work with @Id strategy (encryption disabled)
    var publicData: String? = null
}

@Repository
interface TestDirectIDEntityRepository : EncryptableMongoRepository<TestDirectIDEntity>