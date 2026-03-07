package tech.wanion.encryptable.config

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import tech.wanion.encryptable.mongo.migration.Migration108to109
import kotlin.system.exitProcess

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
                } ?: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %highlight(%-5.5level) [%thread] %logger{0} -- %msg%n"

                // Remove logger name and log level from the pattern
                val modifiedPattern = existingPattern
                    .replace(Regex("""\s*\[%thread]\s*"""), " ")
                    .replace(Regex("""\s*%logger\{0}\s*"""), "")
                    .replace(Regex("""\s*%logger\s*"""), "")
                    .replace(Regex("""\s*--\s*"""), " ")
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
        logger.info("- Storage Threshold is set to ${EncryptableConfig.storageThreshold} bytes.")
        val migrationStatus = if (EncryptableConfig.migration) "enabled" else "disabled"
        logger.info("- Migration is $migrationStatus.")
        logger.info("- ⚠️ Version 1.0.9 requires migration for:")
        logger.info("-   @Id entities with nested List<out Encryptable>.")
        logger.info("- See CHANGELOG.md for more details and new features.")
        logger.info(line)
        if (!EncryptableConfig.migration || isTestEnvironment())
            return
        migrate()
        logger.info("- Exiting application to prevent potential issues. Please disable `encryptable.migration`.")
        logger.info(line)
        exitProcess(0)
    }

    /** Performs database migration if needed */
    private fun migrate() {
        val migrations = listOf(Migration108to109())
        var anyRan = false
        migrations.forEach { migration ->
            if (!migration.shouldMigrate()) return@forEach
            anyRan = true
            logger.info("- Encryptable is Starting Migration from version ${migration.fromVersion()} to version ${migration.toVersion()}.")
            logger.info("- Migration will update the database schema and data as needed to ensure compatibility with this version of Encryptable.")
            logger.info("- Do not stop the application until migration is complete. This may take some time depending on the size of your database.")
            migration.migrateSchema()
            migration.migrateData()
            logger.info("- Migration ${migration.fromVersion()} → ${migration.toVersion()} completed.")
        }
        if (!anyRan)
            logger.info("- No migration needed. Current database is compatible with this version of Encryptable.")
    }
}