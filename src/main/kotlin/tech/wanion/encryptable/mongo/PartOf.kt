package tech.wanion.encryptable.mongo

/**
 * Marks a field as a strong "part-of" relationship in MongoDB persistence.
 * When applied, the annotated field is treated as a composition:
 * - The lifecycle of the child entity depends on the parent entity.
 * - If the parent is deleted, the child is also deleted (cascade delete).
 * - Removing an element from a list annotated with @PartOf will delete the element from the database.
 * If not annotated, the field is treated as a simple reference and only the association is removed, not the entity itself.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class PartOf