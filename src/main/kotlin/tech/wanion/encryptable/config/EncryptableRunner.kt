package tech.wanion.encryptable.config

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class EncryptableRunner : CommandLineRunner {
    /** Logger instance for logging purposes */
    private val logger = LoggerFactory.getLogger(this.javaClass)

    init {
        // Configure shorter log format for this class only by removing logger name and level from existing pattern
        try {
            val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
            loggerContext?.let { ctx ->
                // Get the existing pattern from root logger's console appender
                val rootLogger = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                val existingAppender = rootLogger.getAppender("CONSOLE")
                    ?: rootLogger.iteratorForAppenders().asSequence().firstOrNull()

                val existingPattern = when (existingAppender) {
                    is ConsoleAppender<*> -> {
                        val encoder = existingAppender.encoder
                        if (encoder is PatternLayoutEncoder) encoder.pattern else null
                    }
                    else -> null
                } ?: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger -- %msg%n"

                // Remove logger name and log level from the pattern
                val modifiedPattern = existingPattern
                    .replace(Regex("""\s*\[%thread]\s*"""), " ")
                    .replace(Regex("""\s*%logger\s*"""), "")
                    .replace(Regex("""\s*--\s*"""), "")
                    .trim()

                val classLogger = ctx.getLogger(this.javaClass.name)
                val encoder = PatternLayoutEncoder().apply {
                    context = ctx
                    pattern = modifiedPattern
                    start()
                }
                val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
                    context = ctx
                    this.encoder = encoder
                    start()
                }
                classLogger.detachAndStopAllAppenders()
                classLogger.addAppender(consoleAppender)
                classLogger.isAdditive = false
            }
        } catch (_: Exception) {
            // Fallback: if logback not available, use standard logger
        }
    }

    /** Detects if running in test environment */
    private fun isTestEnvironment(): Boolean {
        return try {
            // Check if JUnit is on the classpath and test runner is active
            Thread.currentThread().stackTrace.any {
                it.className.startsWith("org.junit") ||
                it.className.startsWith("org.gradle.api.internal.tasks.testing")
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Retrieves the version of the Encryptable Framework */
    private fun getEncryptableVersion(): String {
        return if (isTestEnvironment()) {
            "(Test Environment)"
        } else {
            this.javaClass.`package`.implementationVersion ?: "(Unknown Version)"
        }
    }

    /** Executes the command line runner */
    override fun run(vararg args: String) {
        val line = "-".repeat(60)
        logger.info(line)
        logger.info("- Encryptable Framework {}", getEncryptableVersion())
        logger.info("- Single Request Multithreading limited to up to ${EncryptableConfig.threadLimit} threads.")
        val integrityCheckStatus = if (EncryptableConfig.integrityCheck) "enabled" else "disabled"
        logger.info("- Entity Integrity Check is $integrityCheckStatus.")
        logger.info("- GridFS Threshold is set to ${EncryptableConfig.gridFsThreshold} bytes.")
        logger.info(line)
    }
}