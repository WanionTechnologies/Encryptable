package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_files")
class TestFile : Encryptable<TestFile>() {
    @HKDFId
    override var id: CID? = null

    var fileName: String? = null

    // Large file WITHOUT @Encrypt - stored in Storage  but not encrypted
    var publicContent: ByteArray? = null

    // Large file WITH @Encrypt - encrypted then stored in Storage (GridFS or custom) based on size
    @Encrypt
    var privateContent: ByteArray? = null
}

@Repository
interface TestFileRepository : EncryptableMongoRepository<TestFile>