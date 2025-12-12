package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_users")
class TestUser : Encryptable<TestUser>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var email: String? = null

    @Encrypt
    var firstName: String? = null

    @Encrypt
    var lastName: String? = null

    var publicField: String? = null
}

@Repository
interface TestUserRepository : EncryptableMongoRepository<TestUser>