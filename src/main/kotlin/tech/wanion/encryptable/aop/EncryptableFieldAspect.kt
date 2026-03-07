package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.mongo.PartOf
import tech.wanion.encryptable.util.extensions.asClass
import tech.wanion.encryptable.util.extensions.readField
import java.lang.reflect.Field

/**
 * # EncryptableFieldAspect
 * Aspect for intercepting access to `byte[]` fields in classes implementing `Encryptable`.
 * This aspect allows custom logic to be executed before such fields are accessed,for example, for logging or security purposes.
 */
@Aspect
@Suppress("UNCHECKED_CAST", "SpringAopPointcutExpressionInspection")
class EncryptableFieldAspect {
    companion object {
        private const val INIT_METHOD_NAME = "<init>"
    }

    // Helper: Check if field is annotated with @PartOf
    private fun isPartOf(field: Field): Boolean =
        field.getAnnotation(PartOf::class.java) != null

    /**
     * Pointcut to intercept get access to any Encryptable (or subclass) field inside another Encryptable (or subclass).
     */
    @Pointcut("get(tech.wanion.encryptable.mongo.Encryptable+ tech.wanion.encryptable.mongo.Encryptable+.*)")
    fun encryptableFieldGet() {}

    /**
     * Advice to execute before getting an Encryptable field.
     * This advice handles lazy loading of the associated Encryptable entity if it is not already loaded.
     * @param joinPoint The join point representing the field get operation.
     */
    @Before("encryptableFieldGet()")
    fun beforeFieldGet(joinPoint: JoinPoint) {
        val fieldName = joinPoint.signature.name
        val targetEncryptable = joinPoint.target as? Encryptable<*> ?: return
        val field = Encryptable.getMetadataFor(targetEncryptable).encryptableFields[fieldName] ?: return
        val currentValue = field.get(targetEncryptable)
        if (currentValue == null) {
            val encryptableFieldMap = targetEncryptable.readField<MutableMap<String, String>>("encryptableFieldMap")
            val secret = encryptableFieldMap[fieldName] ?: return // No secret, nothing to load.

            val encryptableFieldTypeMap =
                targetEncryptable.readField<MutableMap<String, String>>("encryptableFieldTypeMap")
            val storedType = encryptableFieldTypeMap[fieldName]
            val actualType = if (storedType != null)
                storedType.asClass() as Class<out Encryptable<*>>
            else
                field.type as Class<out Encryptable<*>>
            encryptableFieldTypeMap[fieldName] ?: field.type as Class<out Encryptable<*>>

            // Load the entity from the repository
            val repository = EncryptableContext.getRepositoryForEncryptableClass(actualType)
            val entity = repository.findBySecretOrNull(secret, true)
            field.set(targetEncryptable, entity)
        }
    }

    /**
     * Pointcut to intercept set access to any Encryptable (or subclass) field inside another Encryptable (or subclass).
     */
    @Pointcut("set(tech.wanion.encryptable.mongo.Encryptable+ tech.wanion.encryptable.mongo.Encryptable+.*)")
    fun encryptableFieldSet() {}

    /**
     * Advice to execute before setting an Encryptable field.
     * This advice handles saving or deleting the associated Encryptable entity as needed.
     * It also updates the secret map in the parent entity to reflect changes.
     * @param joinPoint The join point representing the field set operation.
     * @return The result of the field set operation.
     */
    @Around("encryptableFieldSet()")
    fun aroundFieldSet(joinPoint: ProceedingJoinPoint): Any? {

        val fieldName = joinPoint.signature.name

        val stackTrace = Thread.currentThread().stackTrace
        val thirdStack = stackTrace.getOrNull(2) ?: return joinPoint.proceed()
        // Skip constructor calls
        if (thirdStack.methodName == INIT_METHOD_NAME) return joinPoint.proceed()

        val targetEncryptable = joinPoint.target as? Encryptable<*> ?: return joinPoint.proceed()
        val field = Encryptable.getMetadataFor(targetEncryptable).encryptableFields[fieldName] ?: return joinPoint.proceed()
        val fieldType = field.type

        val encryptableFieldMap = targetEncryptable.readField<MutableMap<String, String>>("encryptableFieldMap")
        val encryptableFieldTypeMap = targetEncryptable.readField<MutableMap<String, String>>("encryptableFieldTypeMap")

        var oldEntity = field.get(targetEncryptable) as Encryptable<*>? // Current value before setting new one

        // If oldEntity is null, try to fetch it using the secret from the map.
        if (oldEntity == null && encryptableFieldMap.contains(fieldName)) {
            val secret = encryptableFieldMap[fieldName] ?: throw IllegalStateException("Secret missing for field $fieldName")
            val storedType = encryptableFieldTypeMap[fieldName]
            val actualType = if (storedType != null)
                storedType.asClass() as Class<out Encryptable<*>>
            else
                fieldType as Class<out Encryptable<*>>

            val repository = EncryptableContext.getRepositoryForEncryptableClass(actualType)
            oldEntity = repository.findBySecretOrNull(secret, true)
        }
        val newEntity = joinPoint.args[0] as Encryptable<*>? // New value being set

        // Encryptable has a very robust equals method, so this check is sufficient.
        if (oldEntity == newEntity) return joinPoint.proceed() // No change, nothing to do

        val isPartOf = isPartOf(field)
        when (oldEntity) {
            // if old entity is null
            null -> {
                if (newEntity == null) return joinPoint.proceed() // Both null, nothing to do
                // Old entity is null, new entity is not null
                // Save the new entity
                if (Encryptable.isNew(newEntity)) {
                    val repo = Encryptable.getMetadataFor(newEntity).repository as EncryptableMongoRepository<Encryptable<Nothing>>
                    repo.save(newEntity as Encryptable<Nothing>)
                }
                // Update the secret map
                encryptableFieldMap[fieldName] =
                    if (Encryptable.getMetadataFor(newEntity).isolated) Encryptable.getUnsafeSecretOf(newEntity)
                        ?: throw IllegalStateException("New entity must have a secret after save.")
                    else newEntity.id?.toString()
                        ?: throw IllegalStateException("New entity must have an ID after save.")
                if (newEntity.javaClass != fieldType) {
                    // Store the actual type for future reference
                    encryptableFieldTypeMap[fieldName] = newEntity.javaClass.name
                }
                return joinPoint.proceed()
            }
            // if old entity is not null
            else -> {
                // Remove the reference to the old entity and update the secret map
                encryptableFieldMap.remove(fieldName)
                // Also remove the type mapping
                encryptableFieldTypeMap.remove(fieldName)
                if (newEntity == null) {
                    // New entity is null while old entity is not null
                    // if part-of, delete the old entity.
                    if (isPartOf) {
                        val repo = Encryptable.getMetadataFor(oldEntity).repository as EncryptableMongoRepository<Encryptable<Nothing>>
                        repo.deleteBySecret(Encryptable.getSecretOf(oldEntity))
                    }
                    return joinPoint.proceed()
                } else {
                    // Both old and new entities are not null
                    // if part-of, delete the old entity.
                    if (isPartOf) {
                        val oldRepo = Encryptable.getMetadataFor(oldEntity).repository as EncryptableMongoRepository<Encryptable<Nothing>>
                        oldRepo.deleteBySecret(Encryptable.getSecretOf(oldEntity))
                    }
                    // Save the new entity and update the secret map
                    if (Encryptable.isNew(newEntity)) {
                        val repo = Encryptable.getMetadataFor(newEntity).repository as EncryptableMongoRepository<Encryptable<Nothing>>
                        repo.save(newEntity as Encryptable<Nothing>)
                    }
                    // Update the secret map
                    encryptableFieldMap[fieldName] =
                        if (Encryptable.getMetadataFor(newEntity).isolated) Encryptable.getUnsafeSecretOf(newEntity)
                            ?: throw IllegalStateException("New entity must have a secret after save.")
                        else newEntity.id?.toString()
                            ?: throw IllegalStateException("New entity must have an ID after save.")
                    if (newEntity.javaClass != fieldType)
                        // Store the actual type for future reference
                        encryptableFieldTypeMap[fieldName] = newEntity.javaClass.name
                    return joinPoint.proceed()
                }
            }
        }
    }
}