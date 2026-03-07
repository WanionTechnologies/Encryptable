package cards.project.mongo.entity

import cards.project.storage.MemoryStorage
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.Encrypt
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.mongo.HKDFId

@Document("test_custom_storage")
class TestCustomStorageEntity : Encryptable<TestCustomStorageEntity>() {
    @HKDFId
    override var id: CID? = null

    var name: String? = null

    @MemoryStorage
    @Encrypt
    var memoryContent: ByteArray? = null
}

@Repository
interface TestCustomStorageRepository : EncryptableMongoRepository<TestCustomStorageEntity>