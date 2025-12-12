package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_profiles")
class TestProfile : Encryptable<TestProfile>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var username: String? = null

    @Encrypt
    var email: String? = null

    @Encrypt
    var bio: String? = null

    @Encrypt
    var avatar: ByteArray? = null
}

@Repository
interface TestProfileRepository : EncryptableMongoRepository<TestProfile>