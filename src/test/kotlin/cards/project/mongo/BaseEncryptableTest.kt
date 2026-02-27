package cards.project.mongo

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import tech.wanion.encryptable.util.extensions.randomSecret
import java.security.SecureRandom

/**
 * Base test class for all Encryptable Framework tests.
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
    /** Utility method to generate a random secret string for testing purposes. */
    final fun generateSecret(): String = String.randomSecret()

    /** Utility method to create a sample byte array of specified size in KB. */
    final fun createSampleBytes(sizeInKB: Int): ByteArray {
        val array = ByteArray(sizeInKB * 1024)
        SecureRandom().nextBytes(array)
        return array
    }
}