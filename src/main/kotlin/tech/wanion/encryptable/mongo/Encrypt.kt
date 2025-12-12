package tech.wanion.encryptable.mongo

/**
 * Marks a field for automatic encryption when persisting to the database.
 *
 * **Important:** At runtime, fields annotated with `@Encrypt` contain **decrypted plaintext** data.
 * Developers never interact with the encrypted form - the framework handles encryption/decryption transparently.
 *
 * The annotation describes the **action** that will be performed (encryption on save, decryption on load),
 * not the current runtime state of the field.
 *
 * **Usage:**
 * ```kotlin
 * @Document
 * data class User(
 *     @HKDFId val id: CID,
 *     @Encrypt var email: String,  // Plaintext at runtime, encrypted in database
 *     @Encrypt var name: String
 * ) : Encryptable()
 * ```
 *
 * @see Encryptable
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Encrypt