package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_addresses")
class TestAddress : Encryptable<TestAddress>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var street: String? = null

    @Encrypt
    var city: String? = null

    @Encrypt
    var zipCode: String? = null
}

@Repository
interface TestAddressRepository : EncryptableMongoRepository<TestAddress>