package cards.project.mongo

import cards.project.mongo.entity.TestCustomStorageEntity
import cards.project.mongo.entity.TestCustomStorageRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for custom storage integration using MemoryStorageAnnotation
 */
class EncryptableCustomStorageTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var repository: TestCustomStorageRepository

    @Test
    fun `should store and retrieve data using custom MemoryStorage`() {
        // Given
        val secret = generateSecret()
        val data = createSampleBytes(128) // 128KB
        val entity = TestCustomStorageEntity().withSecret(secret).apply {
            name = "Custom Storage Test"
            memoryContent = data
        }

        // When
        repository.save(entity)
        val retrieved = repository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        retrieved as TestCustomStorageEntity
        assertEquals("Custom Storage Test", retrieved.name)
        assertNotNull(retrieved.memoryContent)
        assertArrayEquals(data, retrieved.memoryContent)

        retrieved.memoryContent = null

        // Cleanup
        repository.deleteBySecret(secret)
    }
}

