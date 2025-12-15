package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import org.springframework.stereotype.Component

/**
 * # EncryptableIDAspect
 *
 * Aspect to enforce controlled setting of the CID (id) field in Encryptable entities.
 *
 * This aspect intercepts any attempt to set the CID field and ensures that it can only be
 * set from within the Encryptable base class methods. This prevents external code from
 * manually setting the CID, which is intended to be managed automatically during entity
 * preparation for persistence.
 *
 * If an attempt is made to set the CID from outside the Encryptable class, an
 * IllegalStateException is thrown with a descriptive error message.
 */
@Aspect
@Component
class EncryptableIDAspect {
    companion object {
        const val ENCRYPTABLE_CLASS = "tech.wanion.encryptable.mongo.Encryptable"
    }

    /**
     * Pointcut to intercept set access to the id field in Encryptable subclasses.
     * Since id is abstract in Encryptable, the actual field exists in subclasses.
     * We intercept any set to CID fields named 'id' in any Encryptable subclass.
     */
    @Pointcut("execution(void tech.wanion.encryptable.mongo.Encryptable+.setId(tech.wanion.encryptable.mongo.CID))")
    fun encryptableIdSet() {}

    /**
     * Advice to execute before setting a CID field.
     * Checks the call stack to determine if the setter was called from within the Encryptable base class.
     * If called from outside, throws an IllegalStateException.
     * @param joinPoint The join point representing the field set operation.
     * @return The result of the setter if called from within Encryptable, otherwise throws exception.
     */
    @Before("encryptableIdSet()")
    fun beforeSetId(joinPoint: JoinPoint) {
        // Check if the call originates from within the Encryptable class
        val stackTrace = Thread.currentThread().stackTrace
        val forthStack = stackTrace.getOrNull(3) ?: return

        if (forthStack.className != ENCRYPTABLE_CLASS) {
            throw IllegalStateException(
                "The CID (id) can only be set from within the Encryptable base class. " +
                        "The CID is automatically set when the entity is prepared for persistence. " +
                        "Do not set this value manually."
            )
        }
    }
}