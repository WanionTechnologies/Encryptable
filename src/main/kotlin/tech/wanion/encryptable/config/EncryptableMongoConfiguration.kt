package tech.wanion.encryptable.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import tech.wanion.encryptable.mongo.converter.ListToNullConverter
import tech.wanion.encryptable.mongo.converter.cid.CIDFromBinary
import tech.wanion.encryptable.mongo.converter.cid.CIDToBinary
import tech.wanion.encryptable.mongo.converter.map.DocumentToMapConverter
import tech.wanion.encryptable.mongo.converter.map.MapToDocumentConverter

/**
 * Configuration class to register custom MongoDB converters.
 */
@Configuration
class EncryptableMongoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MongoCustomConversions::class)
    fun customConversions(): MongoCustomConversions {
        val converters = mutableListOf<Any>(
            CIDFromBinary(),
            CIDToBinary(),
            ListToNullConverter(),
            MapToDocumentConverter(),
            DocumentToMapConverter()
        )
        return MongoCustomConversions(converters)
    }
}