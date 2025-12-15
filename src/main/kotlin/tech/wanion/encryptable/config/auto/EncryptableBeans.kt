package tech.wanion.encryptable.config.auto

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.aop.EncryptableByteFieldAspect
import tech.wanion.encryptable.aop.EncryptableFieldAspect
import tech.wanion.encryptable.aop.EncryptableIDAspect
import tech.wanion.encryptable.aop.EncryptableListFieldAspect
import tech.wanion.encryptable.aop.EncryptableMetadataAspect
import tech.wanion.encryptable.aop.EncryptablePrepareAspect
import tech.wanion.encryptable.mongo.EncryptableInterceptor

@Configuration
class EncryptableBeans {
    @Bean
    fun encryptableInterceptor(): EncryptableInterceptor = EncryptableInterceptor()

    @Bean
    fun encryptableContext(): EncryptableContext = EncryptableContext()

    @Bean
    fun encryptableByteFieldAspect(): EncryptableByteFieldAspect = EncryptableByteFieldAspect()

    @Bean
    fun encryptableFieldAspect(): EncryptableFieldAspect = EncryptableFieldAspect()

    @Bean
    fun encryptableIDAspect(): EncryptableIDAspect = EncryptableIDAspect()

    @Bean
    fun encryptableListFieldAspect(): EncryptableListFieldAspect = EncryptableListFieldAspect()

    @Bean
    fun encryptableMetadataAspect(): EncryptableMetadataAspect = EncryptableMetadataAspect()

    @Bean
    fun encryptablePrepareAspect(): EncryptablePrepareAspect = EncryptablePrepareAspect()
}