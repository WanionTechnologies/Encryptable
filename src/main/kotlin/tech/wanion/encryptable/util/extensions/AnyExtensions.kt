package tech.wanion.encryptable.util.extensions

import tech.wanion.encryptable.EncryptableContext

/**
 * Delegates to EncryptableContext's markForWiping method.
 */
fun markForWiping(vararg objects: Any) = EncryptableContext.markForWiping(objects)