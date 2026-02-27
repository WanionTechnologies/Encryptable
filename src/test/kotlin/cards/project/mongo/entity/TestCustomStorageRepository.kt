package cards.project.mongo.entity

import org.springframework.stereotype.Repository
import tech.wanion.encryptable.mongo.EncryptableMongoRepository

@Repository
interface TestCustomStorageRepository : EncryptableMongoRepository<TestCustomStorageEntity>