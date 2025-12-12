package tech.wanion.encryptable.mongo.converter.cid

import org.bson.types.Binary
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.CID.Companion.cid

/**
 * MongoDB read converter: BSON Binary (subtype 4) -> CID
 */
@ReadingConverter
class CIDFromBinary : Converter<Binary, CID> {
    override fun convert(binary: Binary): CID = binary.cid
}
