package tech.wanion.encryptable.config.auto

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.config.EncryptableRunner
import tech.wanion.encryptable.mongo.EncryptableInterceptor
import tech.wanion.encryptable.storage.GridFSStorage
import tech.wanion.encryptable.storage.StorageHandler

@Configuration
class EncryptableBeans {
    @Bean
    fun encryptableInterceptor(): EncryptableInterceptor = EncryptableInterceptor()

    @Bean
    fun encryptableContext(): EncryptableContext = EncryptableContext()

    @Bean
    fun encryptableStartupRunner(): EncryptableRunner = EncryptableRunner()

    @Bean
    fun storageHandler(): StorageHandler = StorageHandler()

    @Bean
    fun gridFsStorage(): GridFSStorage = GridFSStorage()
}