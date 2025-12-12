package cards.project.mongo.entity

import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import java.time.Instant
import tech.wanion.encryptable.mongo.*

@Document(collection = "test_audit_entities")
class TestAuditEntity : Encryptable<TestAuditEntity>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var data: String? = null

    var accessCount: Int = 0
    var lastAccessedAt: Instant? = null

    override fun touch() {
        accessCount++
        lastAccessedAt = Instant.now()
    }
}

@Repository
interface TestAuditEntityRepository : EncryptableMongoRepository<TestAuditEntity>