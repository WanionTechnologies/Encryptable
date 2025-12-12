package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableList

/**
 * # EncryptableListFieldAspect
 * Aspect for intercepting access to List fields in classes implementing `Encryptable`.
 * This aspect allows custom logic to intercept list getter/setter calls and wrap them in EncryptableList.
 */

@Aspect
class EncryptableListFieldAspect {
    companion object {
        private const val INIT_METHOD_NAME = "<init>"
    }

    // Intercept getter methods that return List types in Encryptable classes
    @Pointcut("execution(java.util.List+ tech.wanion.encryptable.mongo.Encryptable+.get*())")
    fun encryptableListGet() {}

    @Before("encryptableListGet()")
    fun beforeFieldGet(joinPoint: JoinPoint) {
        val methodName = joinPoint.signature.name
        // Convert getter method name to field name (getItems -> items)
        val fieldName = methodName.removePrefix("get").replaceFirstChar { it.lowercase() }
        val target = joinPoint.target as? Encryptable<*> ?: return

        val encryptableListField = target.metadata.encryptableListFields[fieldName] ?: return

        val list = encryptableListField.get(target)
        if (list is EncryptableList<*>) return

        val encryptableList = EncryptableList(fieldName, target)
        encryptableListField.set(target, encryptableList)
    }

    // Intercept setter methods that accept List types in Encryptable classes
    @Pointcut("execution(void tech.wanion.encryptable.mongo.Encryptable+.set*(java.util.List+))")
    fun encryptableListSet() {}

    @Around("encryptableListSet()")
    fun aroundEncryptableListFieldSet(joinPoint: ProceedingJoinPoint): Any? {

        val methodName = joinPoint.signature.name
        // Convert setter method name to field name (setItems -> items)
        val fieldName = methodName.removePrefix("set")
        val target = joinPoint.target as? Encryptable<*> ?: return joinPoint.proceed()

        val stackTrace = Thread.currentThread().stackTrace
        val thirdStack = stackTrace.getOrNull(2) ?: return joinPoint.proceed()
        // The third element (index 2) in the stack trace represents the direct caller method
        // checks if the method name of the third stack frame is the constructor.
        if (thirdStack.methodName == INIT_METHOD_NAME) return joinPoint.proceed()

        val encryptableListFieldMap = target.metadata.encryptableListFields
        if (!encryptableListFieldMap.containsKey(fieldName))
            return joinPoint.proceed()

        // Allow setting list during initial construction/initialization of a new entity
        // This covers common Kotlin builder patterns like `apply { items = ... }` before the entity is saved.
        if (target.isNew()) {
            val encryptableListField = target.metadata.encryptableListFields[fieldName] ?: throw IllegalStateException("Field '$fieldName' not found in metadata.")
            val newValue = joinPoint.args.firstOrNull() ?: return joinPoint.proceed()
            if (newValue is List<*>) {
                // Create EncryptableList with the starting list items
                val encryptableList = EncryptableList(fieldName, target, newValue)
                encryptableListField.set(target, encryptableList)
                return encryptableList
            }
        }

        // For persisted entities, prevent wholesale replacement of the list; enforce using the managed EncryptableList API.
        throw UnsupportedOperationException("Setting field '$fieldName' is not allowed for encryptable list fields.")
    }
}