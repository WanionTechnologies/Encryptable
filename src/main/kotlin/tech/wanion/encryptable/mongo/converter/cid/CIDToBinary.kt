package tech.wanion.encryptable.mongo.converter.cid

import org.bson.types.Binary
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.CID.Companion.binary

/**
 * MongoDB write converter: CID -> BSON Binary (subtype 4)
 */
@WritingConverter
class CIDToBinary : Converter<CID, Binary> {
    override fun convert(cid: CID): Binary = cid.binary
}