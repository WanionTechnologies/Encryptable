package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_nested_objects")
class TestNestedObject : Encryptable<TestNestedObject>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var data: NestedData? = null
}

data class NestedData(
    @Encrypt var innerField: String? = null,
    @Encrypt var innerNumber: Int? = null,
    var publicInner: String? = null
)

@Repository
interface TestNestedObjectRepository : EncryptableMongoRepository<TestNestedObject>