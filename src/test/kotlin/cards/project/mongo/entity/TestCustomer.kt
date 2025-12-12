package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_customers")
class TestCustomer : Encryptable<TestCustomer>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var name: String? = null

    @PartOf
    var billingAddress: TestAddress? = null

    @PartOf
    var shippingAddress: TestAddress? = null
}

@Repository
interface TestCustomerRepository : EncryptableMongoRepository<TestCustomer>