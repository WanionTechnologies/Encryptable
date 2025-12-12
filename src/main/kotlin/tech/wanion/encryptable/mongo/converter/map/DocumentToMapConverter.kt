package tech.wanion.encryptable.mongo.converter.map

import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.util.concurrent.ConcurrentHashMap

/**
 * Converter to read a BSON [Document] from MongoDB and recursively convert it into a [ConcurrentHashMap] with arbitrary key and value types.
 *
 * This converter supports nested documents and lists, ensuring that complex BSON structures are properly deserialized into Kotlin map and list types.
 *
 * - All BSON document keys are strings, but the resulting map uses [Any?] for keys to allow flexibility and symmetry with the writing converter.
 * - Nested documents are recursively converted to [ConcurrentHashMap].
 * - Lists are recursively converted, so lists containing documents or other lists are handled correctly.
 * - Primitives and other BSON-supported types are returned as-is.
 *
 * Corner cases:
 * - All keys in BSON are strings, so the resulting map will have string keys unless post-processed.
 * - Custom objects stored in BSON will be returned as-is and may require additional handling elsewhere.
 *
 * Usage:
 * Register this converter in your Spring Data MongoDB configuration using a [MongoCustomConversions] bean.
 */
@ReadingConverter
class DocumentToMapConverter : Converter<Document, Map<Any?, Any?>> {
    /**
     * Recursively converts a BSON [Document] into a [ConcurrentHashMap].
     *
     * @param source The BSON document to convert.
     * @return A [ConcurrentHashMap] representing the document structure.
     */
    override fun convert(source: Document): Map<Any?, Any?> {
        val map = ConcurrentHashMap<Any?, Any?>()
        for ((key, value) in source) {
            // Skip null keys as ConcurrentHashMap doesn't allow them
            if (key == null) continue
            val convertedValue = convertValue(value)
            // Skip null values as ConcurrentHashMap doesn't allow them either
            if (convertedValue != null) {
                map[key] = convertedValue
            }
        }
        return map
    }

    /**
     * Recursively converts values from BSON to Kotlin types.
     *
     * - Documents are converted to [ConcurrentHashMap].
     * - Lists are recursively converted to mutable lists, handling nested documents and lists.
     * - Primitives and other BSON-supported types are returned as-is.
     *
     * @param value The value to convert.
     * @return The converted value, suitable for use in a [ConcurrentHashMap].
     */
    private fun convertValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Document -> convert(value)
            is List<*> -> value.mapTo(mutableListOf()) { convertValue(it) }
            else -> value
        }
    }
}