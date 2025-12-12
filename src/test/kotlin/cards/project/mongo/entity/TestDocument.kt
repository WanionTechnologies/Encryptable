package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_documents")
class TestDocument : Encryptable<TestDocument>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var title: String? = null

    // Small binary - stored in document
    @Encrypt
    var thumbnail: ByteArray? = null

    // Large binary - stored in GridFS
    @Encrypt
    var pdfContent: ByteArray? = null

    @Encrypt
    var imageContent: ByteArray? = null
}

@Repository
interface TestDocumentRepository : EncryptableMongoRepository<TestDocument>