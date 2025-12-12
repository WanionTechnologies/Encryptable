package tech.wanion.encryptable

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.clear
import tech.wanion.encryptable.util.extensions.ip
import tech.wanion.encryptable.util.extensions.parallelFill
import tech.wanion.encryptable.util.extensions.zerify
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.spec.SecretKeySpec

/**
 * Provides static access to the Spring application context and entity repositories.
 * Used for retrieving repositories for Encryptable entities by class or instance.
 */
@Suppress("UNCHECKED_CAST")
@Component
class EncryptableContext : ApplicationContextAware {
    /**
     * Sets the application context and initializes the repositories map.
     * @param applicationContext The Spring application context.
     */
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        Companion.applicationContext = applicationContext
        repositories = applicationContext.getBeansOfType(EncryptableMongoRepository::class.java)
    }

    /**
     * Holds the application context and a map of entity repositories.
     */
    companion object {
        /** The Spring application context. */
        lateinit var applicationContext: ApplicationContext

        /** Map of repository beans, keyed by bean name. */
        lateinit var repositories: Map<String, EncryptableMongoRepository<out Encryptable<*>>>

        /** Cache of repositories by Encryptable entity class for fast lookup. */
        private val repositoryCache = ConcurrentHashMap<Class<out Encryptable<*>>, EncryptableMongoRepository<*>>()

        /**
         * Retrieves the repository for a given Encryptable entity class.
         * Uses a cache for performance: results are stored and reused for repeated lookups.
         * @param entityClass The class of the Encryptable entity.
         * @return The corresponding repository.
         * @throws NoSuchElementException if no repository is found for the given entity class.
         */
        fun <T : Encryptable<T>> getRepositoryForEncryptableClass(entityClass: Class<out Encryptable<T>>): EncryptableMongoRepository<T> {
            return repositoryCache.computeIfAbsent(entityClass) {
                for (repository in repositories.values) {
                    if (it == repository.getTypeClass())
                        return@computeIfAbsent repository as EncryptableMongoRepository<T>
                }
                throw NoSuchElementException("No repository found for entity class: " + entityClass.name)
            } as EncryptableMongoRepository<T>
        }

        /**
         * Retrieves the client's IP address from the HTTP request.
         *
         * It first checks the `X-Forwarded-For` header, which is commonly used by proxies and
         * load balancers. If not found, it falls back to the remote address of the request.
         *
         * @return The client's IP address as a string, or "test" if called outside a web request context.
         */
        fun getRequestIP(): String = try {
            (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request.ip
        } catch (_: IllegalStateException) {
            // No request context available (e.g., in tests)
            "TEST_ENVIRONMENT"
        }

        /**
         * Thread-local storage for objects to be cleared at the end of each request.
         * Uses a concurrent set to avoid duplicate clearing and provide thread-safe access.
         */
        private val toClearThreadLocal = InheritableThreadLocal<MutableSet<Any>>()

        /**
         * Retrieves the set of objects to be cleared for the current request.
         * Initializes the set if it does not already exist.
         * @return The mutable set of objects to be cleared.
         */
        private fun getToClearSet(): MutableSet<Any>  {
            return toClearThreadLocal.get() ?: Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())).also { toClearThreadLocal.set(it) }
        }

        /**
         * Marks one or more objects for wiping at the end of the request.
         * Supported types: String (zerify), ByteArray (parallelFill(0)), SecretKeySpec (clear).
         * Ensures the same instance is not added twice.
         */
        fun markForWiping(vararg objects: Any) {
            val wipeSet = getToClearSet()
            objects.forEach { obj ->
                when(obj) {
                    is Iterable<*> -> obj.forEach { if (it != null) wipeSet.add(it) }
                    is Array<*> -> obj.forEach { if (it != null) markForWiping(it) }
                    else -> wipeSet.add(obj)
                }
            }
        }

        /**
         * Replaces an object marked for wiping with another object.
         * If the original object is found in the wipe set, it is removed and the replacement is added.
         * @param original The original object to be replaced.
         * @param replacement The new object to add for wiping.
         */
        fun replaceWipingFor(original: Any, replacement: Any) {
            val wipeSet = getToClearSet()
            if (wipeSet.remove(original))
                wipeSet.add(replacement)
        }

        /**
         * Wipes all objects marked for wiping for the current request in parallel and resets the set.
         * Uses a when block to call the appropriate wiping method.
         */
        private fun wipeMarked() {
            val toWipe = getToClearSet()
            toClearThreadLocal.remove()
            toWipe.parallelForEach { obj ->
                when (obj) {
                    is String -> obj.zerify()
                    is ByteArray -> obj.parallelFill(0)
                    is SecretKeySpec -> obj.clear()
                    else -> throw UnsupportedOperationException(
                        "Wipe operation not supported for type: "+obj.javaClass.name+". " +
                        "Only String, ByteArray, and SecretKeySpec are supported. "
                    )
                }
            }
            toWipe.clear()
        }
    }
}