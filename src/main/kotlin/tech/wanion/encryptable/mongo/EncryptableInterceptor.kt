package tech.wanion.encryptable.mongo

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.util.Limited.parallelForEach

/**
 * Interceptor for Encryptable entity repositories.
 * Initializes and clears repository state before and after each request.
 */
@Component
class EncryptableInterceptor : HandlerInterceptor {
    /**
     * Cached companion object instance for reflection calls.
     */
    private val companionInstance by lazy {
        EncryptableContext::class.java.getDeclaredField("Companion").get(null)
    }

    /**
     * Cached method reference to clear secret-related material.
     */
    private val wipeMarked by lazy {
        companionInstance.javaClass.getDeclaredMethod("wipeMarked").apply { isAccessible = true }
    }

    /**
     * Clears all Encryptable repositories after request completion.
     */
    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        EncryptableContext.repositories.values.parallelForEach { it.clearThreadLocal() }
        // Clears secret-related material at the end of the request to limit exposure time.
        wipeMarked.invoke(companionInstance)
    }
}