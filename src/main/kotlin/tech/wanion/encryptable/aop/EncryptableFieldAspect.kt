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
import tech.wanion.encryptable.util.extensions.getField
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
        val field = targetEncryptable.metadata.encryptableFields[fieldName] ?: return
        val currentValue = field.get(targetEncryptable)
        if (currentValue == null) {
            val encryptableFieldMap = targetEncryptable.getField<MutableMap<String, String>>("encryptableFieldMap")
            val secret = encryptableFieldMap[fieldName]
            if (secret != null) {
                val repository = EncryptableContext.getRepositoryForEncryptableClass(field.type as Class<out Encryptable<*>>)
                val entity = repository.findBySecretOrNull(secret)
                field.set(targetEncryptable, entity)
            }
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
        val field = targetEncryptable.metadata.encryptableFields[fieldName] ?: return joinPoint.proceed()

        val encryptableFieldMap = targetEncryptable.getField<MutableMap<String, String>>("encryptableFieldMap")

        var oldEntity = field.get(targetEncryptable) as Encryptable<*>? // Current value before setting new one

        // If oldEntity is null, try to fetch it using the secret from the map.
        if (oldEntity == null && encryptableFieldMap.contains(fieldName)) {
            val secret = encryptableFieldMap[fieldName] ?: throw IllegalStateException("Secret missing for field $fieldName")
            val repository = EncryptableContext.getRepositoryForEncryptableClass(field.type as Class<out Encryptable<*>>)
            oldEntity = repository.findBySecretOrNull(secret)
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
                if (newEntity.isNew()) {
                    val repo = newEntity.metadata.repository as EncryptableMongoRepository<Encryptable<Nothing>>
                    repo.save(newEntity as Encryptable<Nothing>)
                }
                // Update the secret map
                encryptableFieldMap[fieldName] = Encryptable.getSecretOf(newEntity)
                return joinPoint.proceed()
            }
            // if old entity is not null
            else -> {
                if (newEntity == null) {
                    // New entity is null while old entity is not null
                    // if part-of, delete the old entity.
                    if (isPartOf) {
                        val repo = oldEntity.metadata.repository as EncryptableMongoRepository<Encryptable<Nothing>>
                        repo.deleteBySecret(Encryptable.getSecretOf(oldEntity))
                    }
                    // Just remove the reference to the old entity and update the secret map
                    encryptableFieldMap.remove(fieldName)
                    return joinPoint.proceed()
                } else {
                    // Both old and new entities are not null
                    // if part-of, delete the old entity.
                    if (isPartOf) {
                        val oldRepo = oldEntity.metadata.repository as EncryptableMongoRepository<Encryptable<Nothing>>
                        oldRepo.deleteBySecret(Encryptable.getSecretOf(oldEntity))
                    }
                    // Save the new entity and update the secret map
                    if (newEntity.isNew()) {
                        val repo = newEntity.metadata.repository as EncryptableMongoRepository<Encryptable<Nothing>>
                        repo.save(newEntity as Encryptable<Nothing>)
                    }
                    encryptableFieldMap[fieldName] = Encryptable.getSecretOf(newEntity)
                    return joinPoint.proceed()
                }
            }
        }
    }
}