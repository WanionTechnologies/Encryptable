package tech.wanion.encryptable.config.auto

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.config.EncryptableMongoConfiguration
import tech.wanion.encryptable.mongo.EncryptableInterceptor
import tech.wanion.encryptable.util.extensions.zerify

/**
 * Autoconfiguration for the Encryptable Framework.
 * Automatically sets up repositories, lifecycle management, and request interceptors when MongoDB is present.
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate::class, EncryptableMongoConfiguration::class)
@Import(EncryptableMongoConfiguration::class, EncryptableBeans::class)
class EncryptableAutoConfiguration(
) : WebMvcConfigurer {
    /**
     * Application context for accessing beans and resources.
     * Injected by Spring.
     * injecting to make sure it's initialized.
     */
    @Autowired
    lateinit var encryptableContext: EncryptableContext

    /**
     * Interceptor for handling updates/cleaning at request end.
     */
    @Autowired
    lateinit var encryptableInterceptor: EncryptableInterceptor

    init {
        // Check for Kotlin coroutines presence
        try {
            Class.forName("kotlinx.coroutines.CoroutineScope", false, this::class.java.classLoader)
            throw IllegalStateException("Kotlin coroutines detected on classpath. Encryptable is incompatible with coroutines. Please remove kotlinx.coroutines dependencies or isolate Encryptable usage from coroutine code.")
        } catch (_: ClassNotFoundException) {
            // Coroutines not present, continue as normal
        }
        // Perform zerification sanity checks
        try {
            // This will throw if zerification is not working properly
            "Should be Zerified".zerify()
        } catch (e: Exception) {
            throw IllegalStateException("Encryptable failed to initialize properly. Ensure that the required JVM arguments --add-opens java.base/javax.crypto.spec=ALL-UNNAMED and --add-opens java.base/java.lang=ALL-UNNAMED are set, as documented in the prerequisites. Original error: ${e.message}", e)
        }
    }

    /**
     * Registers the EncryptableInterceptor to handle request lifecycle events.
     *
     * @param registry the interceptor registry
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(encryptableInterceptor)
    }
}