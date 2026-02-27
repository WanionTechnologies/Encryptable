package cards.project.mongo.storage

import tech.wanion.encryptable.storage.Storage

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Storage(storageClass = MemoryStorageImpl::class)
annotation class MemoryStorage