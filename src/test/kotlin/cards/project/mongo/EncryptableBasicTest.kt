package cards.project.mongo

import cards.project.mongo.entity.TestUser
import cards.project.mongo.entity.TestUserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tech.wanion.encryptable.mongo.Encryptable.Companion.asSecretOf

/**
 * Basic usage tests for Encryptable framework
 * Tests fundamental CRUD operations and encryption/decryption
 */
class EncryptableBasicTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var userRepository: TestUserRepository

    @Test
    fun `should save and retrieve entity with encrypted fields`() {
        // Given
        val secret = generateSecret()
        val user = TestUser().withSecret(secret).apply {
            email = "test@example.com"
            firstName = "John"
            lastName = "Doe"
            publicField = "public-value"
        }

        // When
        userRepository.save(user)
        val retrieved = userRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals("test@example.com", retrieved?.email)
        assertEquals("John", retrieved?.firstName)
        assertEquals("Doe", retrieved?.lastName)
        assertEquals("public-value", retrieved?.publicField)
        assertNotNull(retrieved?.id)

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    @Test
    fun `should generate deterministic ID from secret`() {
        // Given
        val secret = generateSecret()
        val user1 = TestUser().withSecret(secret).apply {
            email = "test1@example.com"
        }
        val user2 = TestUser().withSecret(secret).apply {
            email = "test2@example.com"
        }

        // When
        userRepository.save(user1)
        val id1 = user1.id

        // Then - same secret should generate same ID
        userRepository.deleteBySecret(secret)

        userRepository.save(user2)
        val id2 = user2.id

        assertEquals(id1, id2, "Same secret should generate same deterministic ID")

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    @Test
    fun `should check if entity exists by secret`() {
        // Given
        val secret = generateSecret()
        val user = TestUser().withSecret(secret).apply {
            email = "exists@example.com"
        }

        // When
        userRepository.save(user)
        val exists = userRepository.existsBySecret(secret)

        // Then
        assertTrue(exists)

        // Cleanup
        userRepository.deleteBySecret(secret)

        val existsAfterDelete = userRepository.existsBySecret(secret)
        assertFalse(existsAfterDelete)
    }

    @Test
    fun `should return null for non-existent secret`() {
        // Given
        val nonExistentSecret = generateSecret()

        // When
        val result = userRepository.findBySecretOrNull(nonExistentSecret)

        // Then
        assertNull(result)
    }

    @Test
    fun `should update entity fields automatically`() {
        // Given
        val secret = generateSecret()
        val user = TestUser().withSecret(secret).apply {
            email = "original@example.com"
            firstName = "Original"
        }
        userRepository.save(user)

        // When
        val retrieved = userRepository.findBySecretOrNull(secret)
        retrieved?.email = "updated@example.com"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        userRepository.flushThenClear()

        // Then
        val afterUpdate = userRepository.findBySecretOrNull(secret)
        assertEquals("updated@example.com", afterUpdate?.email)
        assertEquals("Original", afterUpdate?.firstName) // Unchanged field

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle batch operations`() {
        // Given
        val secrets = (1..3).map { generateSecret() }
        val users = secrets.mapIndexed { index, secret ->
            TestUser().withSecret(secret).apply {
                email = "user$index@example.com"
                firstName = "User$index"
            }
        }

        // When
        userRepository.saveAll(users)
        val retrieved = userRepository.findBySecrets(secrets)

        // Then
        assertEquals(3, retrieved.size)
        assertTrue(retrieved.all { it.email?.startsWith("user") == true })

        // Cleanup
        userRepository.deleteBySecrets(secrets)
    }

    @Test
    fun `should use alternative retrieval syntax`() {
        // Given
        val secret = generateSecret()
        val user = TestUser().withSecret(secret).apply {
            email = "alternative@example.com"
        }
        userRepository.save(user)

        // When
        val retrieved = secret.asSecretOf<TestUser>()

        // Then
        assertNotNull(retrieved)
        assertEquals("alternative@example.com", retrieved?.email)

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle null encrypted fields`() {
        // Given
        val secret = generateSecret()
        val user = TestUser().withSecret(secret).apply {
            email = "test@example.com"
            firstName = null // Null encrypted field
            lastName = null
        }

        // When
        userRepository.save(user)
        val retrieved = userRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals("test@example.com", retrieved?.email)
        assertNull(retrieved?.firstName)
        assertNull(retrieved?.lastName)

        // Cleanup
        userRepository.deleteBySecret(secret)
    }
}