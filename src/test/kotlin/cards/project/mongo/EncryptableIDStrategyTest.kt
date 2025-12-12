package cards.project.mongo

import cards.project.mongo.entity.TestDirectIDEntity
import cards.project.mongo.entity.TestDirectIDEntityRepository
import cards.project.mongo.entity.TestHKDFEntity
import cards.project.mongo.entity.TestHKDFEntityRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import tech.wanion.encryptable.mongo.CID

/**
 * Tests for different ID strategies (@Id vs @HKDFId)
 * Verifies deterministic ID generation and encryption behavior
 */
class EncryptableIDStrategyTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var hkdfRepository: TestHKDFEntityRepository

    @Autowired
    private lateinit var directIdRepository: TestDirectIDEntityRepository

    private fun generateCIDString(): String {
        return CID.randomCIDString()
    }

    @Test
    fun `HKDF strategy should generate deterministic ID from secret`() {
        // Given
        val secret = generateSecret()

        // When - create two entities with same secret
        val entity1 = TestHKDFEntity().withSecret(secret).apply {
            sensitiveData = "Entity 1"
        }
        hkdfRepository.save(entity1)
        val id1 = entity1.id
        hkdfRepository.deleteBySecret(secret)

        val entity2 = TestHKDFEntity().withSecret(secret).apply {
            sensitiveData = "Entity 2"
        }
        hkdfRepository.save(entity2)
        val id2 = entity2.id

        // Then - same secret should generate same ID
        assertEquals(id1, id2, "HKDF should generate deterministic ID from secret")

        // Cleanup
        hkdfRepository.deleteBySecret(secret)
    }

    @Test
    fun `HKDF strategy should support encryption`() {
        // Given
        val secret = generateSecret()
        val entity = TestHKDFEntity().withSecret(secret).apply {
            sensitiveData = "This should be encrypted"
        }

        // When
        hkdfRepository.save(entity)

        val retrieved = hkdfRepository.findBySecretOrNull(secret)

        // Then - data should be encrypted and decrypted
        // Verify by checking retrieved value matches original.
        // cannot verify encryption directly without access to DB.
        assertNotNull(retrieved)
        assertEquals("This should be encrypted", retrieved?.sensitiveData)

        // Cleanup
        hkdfRepository.deleteBySecret(secret)
    }

    @Test
    fun `Direct ID strategy should use secret as CID`() {
        // Given - secret must be 22 characters Base64 to form valid CID
        val cidString = generateCIDString()
        println("Generated cidString: '$cidString' (length: ${cidString.length})")

        // When
        val entity = TestDirectIDEntity().withSecret(cidString).apply {
            publicData = "Public information"
        }
        directIdRepository.save(entity)

        // Then - ID should be the secret itself converted to CID
        assertNotNull(entity.id)
        // Verify ID matches the CID derived from secret
        assertEquals(cidString, entity.id.toString())

        // Cleanup
        directIdRepository.deleteBySecret(cidString)
    }

    @Test
    fun `HKDF strategy should work with high entropy secrets`() {
        // Given - various secret formats
        val secrets = listOf(
            generateSecret(), // Base64 encoded random
            "my-very-long-high-entropy-passphrase-with-special-chars-!@#$%",
            CID.randomCIDString() + UUID.randomUUID().toString() // CID + UUID
        )

        // When/Then - all should work with HKDF
        secrets.forEach { secret ->
            val entity = TestHKDFEntity().withSecret(secret).apply {
                sensitiveData = "Test with $secret"
            }
            hkdfRepository.save(entity)
            assertNotNull(entity.id, "HKDF should generate ID for any secret format")
            hkdfRepository.deleteBySecret(secret)
        }
    }

    @Test
    fun `different secrets should generate different IDs with HKDF`() {
        // Given
        val secret1 = generateSecret()
        val secret2 = generateSecret()

        // When
        val entity1 = TestHKDFEntity().withSecret(secret1).apply {
            sensitiveData = "Entity 1"
        }
        hkdfRepository.save(entity1)

        val entity2 = TestHKDFEntity().withSecret(secret2).apply {
            sensitiveData = "Entity 2"
        }
        hkdfRepository.save(entity2)

        // Then
        assertNotEquals(entity1.id, entity2.id, "Different secrets should produce different IDs")

        // Cleanup
        hkdfRepository.deleteBySecret(secret1)
        hkdfRepository.deleteBySecret(secret2)
    }

    @Test
    fun `should retrieve entity by HKDF derived ID`() {
        // Given
        val secret = generateSecret()
        val entity = TestHKDFEntity().withSecret(secret).apply {
            sensitiveData = "Retrieval test"
        }
        hkdfRepository.save(entity)
        val savedId = entity.id

        // When - retrieve by secret
        val retrieved = hkdfRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals(savedId, retrieved?.id)
        assertEquals("Retrieval test", retrieved?.sensitiveData)

        // Cleanup
        hkdfRepository.deleteBySecret(secret)
    }

    @Test
    fun `HKDF should be consistent across application restarts`() {
        // Given - same secret used multiple times
        val secret = generateSecret()
        val ids = mutableListOf<CID>()

        // When - create and delete entity 3 times
        repeat(3) {
            val entity = TestHKDFEntity().withSecret(secret).apply {
                sensitiveData = "Iteration $it"
            }
            hkdfRepository.save(entity)
            ids.add(entity.id!!)
            hkdfRepository.deleteBySecret(secret)

            // simulate the end of the request.
            // this shouldn't be done manually in real code.
            hkdfRepository.flushThenClear()
        }

        // Then - all IDs should be identical
        assertTrue(
            ids.all { it == ids[0] },
            "HKDF should generate same ID for same secret across multiple saves"
        )
    }
}