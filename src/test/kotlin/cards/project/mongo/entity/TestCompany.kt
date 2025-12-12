package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_companies")
class TestCompany : Encryptable<TestCompany>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var companyName: String? = null

    // Reference WITHOUT @PartOf - should not cascade delete
    @Encrypt
    var headquarters: TestAddress? = null
}

@Repository
interface TestCompanyRepository : EncryptableMongoRepository<TestCompany>