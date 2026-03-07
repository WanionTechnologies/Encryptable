package tech.wanion.encryptable.mongo

/**
 * Marks a nested `Encryptable` field (or `List<Encryptable>` field) as a simple reference.
 *
 * When applied, only the child entity's **ID** is stored in the parent document instead of the
 * child's secret. This is the correct choice when the parent does not own the child's secret —
 * for example, when a shared resource is referenced by multiple unrelated parents.
 *
 * ## Behavior
 *
 * - **Load:** The parent can still load the child entity by its stored ID. The child object will
 *   be present and its non-encrypted fields will be readable.
 * - **Decrypt:** The parent **cannot** decrypt the child's `@Encrypt` fields — it does not hold
 *   the child's secret. Attempting to read an encrypted field on the loaded child will return the
 *   raw ciphertext (the entity will be in an errored state for that field).
 * - **Delete:** The parent **can** delete the child entity. For single `Encryptable` fields,
 *   `@PartOf` cascade is sufficient. For `List<Encryptable>` fields, `@PartOf` **must** be
 *   present on the list field itself — without it, removing items from the list or deleting
 *   the parent will **not** cascade-delete the child entities.
 *
 * ## When to use
 *
 * Use `@SimpleReference` on an `@HKDFId` parent when:
 * - The child entity is owned and decrypted independently (e.g. by a different user or service).
 * - You only need to maintain a navigable link and/or the ability to delete the child, not to
 *   read its encrypted content through the parent.
 *
 * Do **not** use `@SimpleReference` if the parent needs to read the child's encrypted fields —
 * use a regular (secret-storing) reference instead.
 *
 * ## Example
 *
 * ```kotlin
 * class Order : Encryptable<Order>() {
 *     @HKDFId override var id: CID? = null
 *
 *     // The parent stores only the Product's ID.
 *     // It can load and delete the Product, but cannot decrypt its @Encrypt fields.
 *     @SimpleReference
 *     @PartOf
 *     var product: Product? = null
 * }
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class SimpleReference