package tech.wanion.encryptable.mongo.converter

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.util.extensions.isListOf

/**
 * A custom converter to handle lists of encryptable items by converting them to null during write operations.
 * Encryptable fields are managed separately, so this converter ensures they are not directly written to the database.
 */
@WritingConverter
class ListToNullConverter : Converter<List<Any>, Any> {
    override fun convert(source: List<Any>): Any? {
        return if (source.isEmpty() || source.isListOf(Encryptable::class.java))
            null
        else source
    }
}