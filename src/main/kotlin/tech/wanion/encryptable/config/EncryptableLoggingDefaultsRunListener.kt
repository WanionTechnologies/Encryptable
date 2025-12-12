package tech.wanion.encryptable.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringApplicationRunListener
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext
import org.springframework.core.Ordered

/**
 * Ensures full ISO-8601 timestamps are present in logs by setting logging patterns
 * via system properties as early as possible in the Spring Boot startup lifecycle.
 *
 * This runs before the logging system initializes, so it reliably applies defaults
 * even if no application.properties is present. Users can still override using
 * system properties, environment variables, or application properties.
 */
class EncryptableLoggingDefaultsRunListener(app: SpringApplication, args: Array<String>) : SpringApplicationRunListener, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun starting(bootstrapContext: ConfigurableBootstrapContext) {
        // Only set defaults if not already provided by the user
        setIfMissing("logging.pattern.console")
        setIfMissing("logging.pattern.file")
    }

    private fun setIfMissing(key: String) {
        if (System.getProperty(key).isNullOrEmpty() && System.getenv(key.replace('.', '_').uppercase()).isNullOrEmpty()) {
            System.setProperty(key, "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger -- %msg%n")
        }
    }
}
