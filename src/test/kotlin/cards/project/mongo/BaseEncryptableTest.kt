package cards.project.mongo

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import tech.wanion.encryptable.util.extensions.randomSecret

/**
 * Base test class for all Encryptable framework tests.
 *
 * `projectcards` was the code name during early development and has been retained as the legacy database name.
 *
 * This ensures that all tests use a dedicated test context and configuration:
 * - No dependency on any main application class
 * - Configures test MongoDB connection
 * - Provides common setup for all tests
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.data.mongodb.host=localhost",
        "spring.data.mongodb.port=27017",
        "spring.data.mongodb.database=projectcards",
        "server.port=0",
        "spring.main.allow-bean-definition-overriding=true"
    ]
)
abstract class BaseEncryptableTest {
    final fun generateSecret(): String = String.randomSecret()
}