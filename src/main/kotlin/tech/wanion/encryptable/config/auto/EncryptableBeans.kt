package tech.wanion.encryptable.config.auto

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.mongo.EncryptableInterceptor

@Configuration
class EncryptableBeans {
    @Bean
    fun encryptableInterceptor(): EncryptableInterceptor = EncryptableInterceptor()

    @Bean
    fun encryptableContext(): EncryptableContext = EncryptableContext()
}