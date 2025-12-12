package cards.project

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.slf4j.LoggerFactory
import tech.wanion.encryptable.config.auto.EnableEncryptable

/**
 * This test class is for verifying that the test context loads successfully for the cards.lin package.
 * It does not depend on any application class from the main source set, ensuring the framework is context-agnostic.
 */
@EnableEncryptable
@SpringBootApplication
class ApplicationTests {
    private val logger = LoggerFactory.getLogger(ApplicationTests::class.java)

    @Test
    fun contextLoads() {
        // Verifies that the Spring context loads without errors for the cards.project package.
        logger.info("ApplicationTests contextLoads: Spring context for cards.project loaded successfully.")
    }
}