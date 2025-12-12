package cards.project.mongo

import cards.project.mongo.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Integration tests for complex scenarios
 * Tests edge cases, error handling, and advanced usage patterns
 */
class EncryptableIntegrationTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var complexRepository: TestComplexEntityRepository

    @Autowired
    private lateinit var nestedRepository: TestNestedObjectRepository

    @Test
    fun `should handle entity with multiple encrypted field types`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = "Encrypted string"
            stringList = mutableListOf("Item1", "Item2", "Item3")
            binaryField = ByteArray(256) { it.toByte() }
            publicField = "Public data"
            numberField = 42
        }

        // When
        complexRepository.save(entity)

        val retrieved = complexRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals("Encrypted string", retrieved?.stringField)
        assertEquals(3, retrieved?.stringList?.size)
        assertEquals("Item1", retrieved?.stringList?.get(0))
        assertEquals(256, retrieved?.binaryField?.size)
        assertEquals("Public data", retrieved?.publicField)
        assertEquals(42, retrieved?.numberField)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should encrypt nested objects with @Encrypt annotation`() {
        // Given
        val secret = generateSecret()
        val nestedData = NestedData(
            innerField = "Encrypted inner",
            innerNumber = 999,
            publicInner = "Public inner"
        )
        val entity = TestNestedObject().withSecret(secret).apply {
            data = nestedData
        }

        // When
        nestedRepository.save(entity)

        val retrieved = nestedRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertNotNull(retrieved?.data)
        assertEquals("Encrypted inner", retrieved?.data?.innerField)
        assertEquals(999, retrieved?.data?.innerNumber)
        assertEquals("Public inner", retrieved?.data?.publicInner)

        // Cleanup
        nestedRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle concurrent operations on different entities`() {
        // Given
        val secrets = (1..5).map { generateSecret() }
        val entities = secrets.mapIndexed { index, secret ->
            TestComplexEntity().withSecret(secret).apply {
                stringField = "Entity $index"
                numberField = index
            }
        }

        // When - save all
        complexRepository.saveAll(entities)

        // Then - all should be retrievable
        val retrieved = complexRepository.findBySecrets(secrets)
        assertEquals(5, retrieved.size)

        // Verify each entity
        retrieved.forEachIndexed { _, entity ->
            assertTrue(entity.stringField?.contains("Entity") == true)
        }

        // Cleanup
        complexRepository.deleteBySecrets(secrets)
    }

    @Test
    fun `should handle empty string list`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = "Test"
            stringList = mutableListOf() // Empty list
        }

        // When
        complexRepository.save(entity)

        val retrieved = complexRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertTrue(retrieved?.stringList?.isEmpty() == true)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle string list modifications`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = "List test"
            stringList = mutableListOf("A", "B", "C")
        }
        complexRepository.save(entity)

        // When - modify list
        val retrieved = complexRepository.findBySecretOrNull(secret)
        retrieved?.stringList?.add("D")
        retrieved?.stringList?.removeAt(0) // Remove "A"
        complexRepository.flushThenClear()

        // Then
        val afterUpdate = complexRepository.findBySecretOrNull(secret)
        assertEquals(3, afterUpdate?.stringList?.size) // B, C, D
        assertEquals("B", afterUpdate?.stringList?.get(0))
        assertEquals("D", afterUpdate?.stringList?.get(2))

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should preserve data integrity across multiple updates`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = "Original"
            numberField = 1
        }
        complexRepository.save(entity)

        // When - perform multiple updates
        repeat(5) { iteration ->
            val retrieved = complexRepository.findBySecretOrNull(secret)
            retrieved?.stringField = "Update $iteration"
            retrieved?.numberField = iteration

            // simulate the end of the request.
            // this shouldn't be done manually in real code.
            complexRepository.flushThenClear()
        }

        // Then - final state should reflect last update
        val final = complexRepository.findBySecretOrNull(secret)
        assertEquals("Update 4", final?.stringField)
        assertEquals(4, final?.numberField)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle entity with all null encrypted fields`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = null
            stringList = mutableListOf()
            binaryField = null
            publicField = "Only public has value"
            numberField = 123
        }

        // When
        complexRepository.save(entity)

        val retrieved = complexRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertNull(retrieved?.stringField)
        assertTrue(retrieved?.stringList?.isEmpty() == true)
        assertNull(retrieved?.binaryField)
        assertEquals("Only public has value", retrieved?.publicField)
        assertEquals(123, retrieved?.numberField)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle binary data with special byte values`() {
        // Given
        val secret = generateSecret()
        val specialBytes = byteArrayOf(0, -1, 127, -128, 1, 255.toByte())
        val entity = TestComplexEntity().withSecret(secret).apply {
            binaryField = specialBytes
        }

        // When
        complexRepository.save(entity)

        val retrieved = complexRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertArrayEquals(specialBytes, retrieved?.binaryField)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle rapid successive updates`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = "Initial"
        }
        complexRepository.save(entity)

        // When - rapid updates
        val retrieved = complexRepository.findBySecretOrNull(secret)
        retrieved?.stringField = "First update"
        retrieved?.numberField = 1

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        complexRepository.flushThenClear()

        val retrieved2 = complexRepository.findBySecretOrNull(secret)
        retrieved2?.stringField = "Second update"
        retrieved2?.numberField = 2

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        complexRepository.flushThenClear()

        val retrieved3 = complexRepository.findBySecretOrNull(secret)
        retrieved3?.stringField = "Third update"
        retrieved3?.numberField = 3

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        complexRepository.flushThenClear()

        // Then
        val final = complexRepository.findBySecretOrNull(secret)
        assertEquals("Third update", final?.stringField)
        assertEquals(3, final?.numberField)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle unicode and special characters in encrypted strings`() {
        // Given
        val secret = generateSecret()
        val specialStrings = listOf(
            "Hello 世界", // Chinese
            "Привет мир", // Russian
            "مرحبا بالعالم", // Arabic
            "🎉🎊🎈", // Emojis
            "Special: !@#$%^&*()"
        )

        val entity = TestComplexEntity().withSecret(secret).apply {
            stringList = specialStrings.toMutableList()
        }

        // When
        complexRepository.save(entity)

        val retrieved = complexRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals(specialStrings.size, retrieved?.stringList?.size)
        specialStrings.forEachIndexed { index, expected ->
            assertEquals(expected, retrieved?.stringList?.get(index))
        }

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    @Test
    fun `should maintain data consistency after failed retrieval attempt`() {
        // Given
        val secret = generateSecret()
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringField = "Consistent data"
        }
        complexRepository.save(entity)

        // When - try to retrieve with wrong secret
        val wrongSecret = generateSecret()
        val notFound = complexRepository.findBySecretOrNull(wrongSecret)
        assertNull(notFound)

        // Then - should still retrieve with correct secret
        val retrieved = complexRepository.findBySecretOrNull(secret)
        assertNotNull(retrieved)
        assertEquals("Consistent data", retrieved?.stringField)

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }
}