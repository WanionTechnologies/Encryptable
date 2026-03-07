package tech.wanion.encryptable.mongo.converter

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

/**
 * A custom converter to handle lists of encryptable items by converting them to null during write operations.
 */
@WritingConverter
class ListToNullConverter : Converter<List<Any>, Any?> {
    override fun convert(source: List<Any>): Any? {
        return source.ifEmpty { null }
    }
}