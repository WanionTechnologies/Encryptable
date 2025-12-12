package tech.wanion.encryptable.config.auto

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import tech.wanion.encryptable.mongo.EncryptableMongoRepositoryImpl

/**
 * Enables the Encryptable Framework autoconfiguration.
 * This annotation should be placed on your main Spring Boot application class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Configuration
@EnableAspectJAutoProxy
@EnableMongoRepositories(repositoryBaseClass = EncryptableMongoRepositoryImpl::class)
@Import(EncryptableAutoConfiguration::class)
annotation class EnableEncryptable