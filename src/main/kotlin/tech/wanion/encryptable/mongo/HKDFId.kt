package tech.wanion.encryptable.mongo

import org.springframework.data.annotation.Id

/**
 * Annotation for marking a field as the deterministic MongoDB CID using HKDF-based derivation.
 *
 * Usage:
 * - Apply this annotation to an entity's ID field to indicate that it should be generated using HKDF from a high-entropy secret.
 * - The field must be of type `CID` and will be used as the unique identifier for the entity in MongoDB.
 * - The secret used for HKDF derivation should be a high-entropy string (typically 50+ characters).
 * - This annotation is mutually exclusive with `@Id`—use one or the other for the entity's identifier field.
 *
 * Implementation Details:
 * - When present, the framework will use HKDF to derive a 16-byte value from the secret and generate the CID.
 * - The annotation is retained at runtime and targets fields only.
 * - Used in conjunction with Encryptable entities to support secure, deterministic IDs for encrypted data.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
@Id
annotation class HKDFId