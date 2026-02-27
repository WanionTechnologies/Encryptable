package tech.wanion.encryptable.config

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment

class EncryptableMigrationSchedulerDisabler : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val env: ConfigurableEnvironment = applicationContext.environment
        val migration = env.getProperty("encryptable.migration")?.toBoolean() ?: false
        if (migration)
            env.systemProperties["spring.task.scheduling.enabled"] = false
    }
}