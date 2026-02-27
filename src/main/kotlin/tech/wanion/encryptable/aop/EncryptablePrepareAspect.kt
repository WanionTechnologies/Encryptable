package tech.wanion.encryptable.aop

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.extensions.getBean

/**
 * # EncryptablePrepareAspect
 * Aspect for intercepting Encryptable.prepare() to process byte array fields before entity is persisted.
 */
@Aspect
class EncryptablePrepareAspect {
    companion object {
        private val prepareMethod = StorageHandler::class.java.getDeclaredMethod(
            "prepare",
            Encryptable::class.java
        ).also { it.isAccessible = true }
    }

    /** StorageHandler instance for handling storage operations related to byte[] fields. */
    val storageHandler: StorageHandler = getBean(StorageHandler::class.java)

    // Pointcut for execution of Encryptable.prepare()
    @Pointcut("execution(* tech.wanion.encryptable.mongo.Encryptable+.prepare(..))")
    fun beforePreparePointcut() {}

    /**
     * Intercepts execution of Encryptable.prepare() method.
     *
     * This method is executed before the actual prepare() method is called. It checks if the target object is an instance of Encryptable,
     * and then calls the storage handler's prepare method to perform any necessary processing related to byte array fields, such as moving large byte arrays to GridFS and updating references.
     *
     * @param joinPoint The join point representing the intercepted method execution, providing access to method details and target object.
     */
    @Before("beforePreparePointcut()")
    fun beforePrepare(joinPoint: JoinPoint) {
        val encryptable = joinPoint.target as? Encryptable<*> ?: return

        prepareMethod.invoke(storageHandler, encryptable)
    }
}