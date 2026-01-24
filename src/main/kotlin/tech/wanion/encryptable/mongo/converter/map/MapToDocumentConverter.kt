package tech.wanion.encryptable.mongo.converter.map

import org.bson.Document
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions

/**
 * Converter to persist a generic [Map] as a BSON [Document] in MongoDB.
 *
 * This converter recursively serializes any [Map] with arbitrary key and value types into a BSON [Document],
 * making it suitable for storage in MongoDB collections. It supports nested maps and lists, ensuring that
 * complex structures are properly converted into BSON documents and arrays.
 *
 * - All map keys are converted to strings using [Any?.toString], as BSON documents require string keys.
 * - Nested maps are recursively converted to [Document].
 * - Lists are recursively converted, so lists containing maps or other lists are handled correctly.
 * - Primitives and other BSON-supported types are stored as-is.
 *
 * Corner cases:
 * - Non-string keys are stringified, which may cause ambiguity if keys like `1` and `"1"` are present.
 * - If a key is `null`, it will be converted to the string "null".
 * - Custom objects as values will be stored as-is and may require additional handling if not BSON-serializable.
 * - Duplicate keys after stringification may overwrite values.
 *
 * Usage:
 * Register this converter in your Spring Data MongoDB configuration using a [MongoCustomConversions] bean
 * to enable automatic serialization of generic maps to BSON documents.
 */
@Suppress("UNCHECKED_CAST")
@WritingConverter
class MapToDocumentConverter : Converter<Map<Any?, Any?>, Document?> {
    /**
     * Recursively converts a [Map] into a BSON [Document].
     *
     * @param source The map to convert.
     * @return A BSON [Document] representing the map structure, or an empty Document if the map is empty.
     */
    override fun convert(source: Map<Any?, Any?>): Document? {
        if (source.isEmpty()) return null
        val doc = Document()
        for ((key, value) in source) {
            val stringKey = key.toString()
            doc[stringKey] = convertValue(value)
        }
        return doc
    }

    /**
     * Recursively converts values from generic types to BSON-compatible types.
     *
     * - Maps are converted to [Document].
     * - Lists are recursively converted, handling nested maps and lists.
     * - Primitives and other BSON-supported types are returned as-is.
     *
     * @param value The value to convert.
     * @return The converted value, suitable for use in a BSON [Document].
     */
    private fun convertValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> convert(value as Map<Any?, Any?>)
            is List<*> -> value.map { convertValue(it) }
            else -> value
        }
    }
}