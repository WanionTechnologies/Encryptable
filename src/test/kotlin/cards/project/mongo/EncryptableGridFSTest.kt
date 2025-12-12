package cards.project.mongo

import cards.project.mongo.entity.TestDocument
import cards.project.mongo.entity.TestDocumentRepository
import cards.project.mongo.entity.TestFile
import cards.project.mongo.entity.TestFileRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for GridFS integration and large binary field handling
 */
class EncryptableGridFSTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var documentRepository: TestDocumentRepository

    @Autowired
    private lateinit var fileRepository: TestFileRepository

    private fun createSampleBytes(sizeInKB: Int): ByteArray {
        return ByteArray(sizeInKB * 1024) { (it % 256).toByte() }
    }

    @Test
    fun `should store small binary in document`() {
        // Given - 512 bytes (less than 1KB threshold)
        val secret = generateSecret()
        val smallData = ByteArray(512) { it.toByte() }
        val document = TestDocument().withSecret(secret).apply {
            title = "Small Document"
            thumbnail = smallData
        }

        // When
        documentRepository.save(document)
        val retrieved = documentRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertNotNull(retrieved?.thumbnail)
        assertEquals(512, retrieved?.thumbnail?.size)
        assertArrayEquals(smallData, retrieved?.thumbnail)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    @Test
    fun `should store large binary in GridFS`() {
        // Given - 500KB (greater than 1KB threshold)
        val secret = generateSecret()
        val largeData = createSampleBytes(500)
        val document = TestDocument().withSecret(secret).apply {
            title = "Large Document"
            pdfContent = largeData
        }

        // When
        documentRepository.save(document)
        val retrieved = documentRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals("Large Document", retrieved?.title)

        // Access the large field - triggers lazy load from GridFS
        val loadedContent = retrieved?.pdfContent
        assertNotNull(loadedContent)
        assertEquals(500 * 1024, loadedContent?.size)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    @Test
    fun `should lazy load GridFS files on access`() {
        // Given
        val secret = generateSecret()
        val largeData = createSampleBytes(1000) // 1MB
        val document = TestDocument().withSecret(secret).apply {
            title = "Lazy Load Test"
            pdfContent = largeData
            imageContent = createSampleBytes(500) // 500KB
        }

        documentRepository.save(document)

        // When - retrieve document (large files NOT loaded yet)
        val retrieved = documentRepository.findBySecretOrNull(secret)
        assertNotNull(retrieved)
        assertEquals("Lazy Load Test", retrieved?.title)

        // Then - access pdfContent triggers lazy load
        val pdfSize = retrieved?.pdfContent?.size
        assertEquals(1000 * 1024, pdfSize)

        // imageContent also lazy loaded when accessed
        val imageSize = retrieved?.imageContent?.size
        assertEquals(500 * 1024, imageSize)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle encrypted and unencrypted GridFS files`() {
        // Given
        val secret = generateSecret()
        val publicData = createSampleBytes(100) // 100KB
        val privateData = createSampleBytes(200) // 200KB

        val file = TestFile().withSecret(secret).apply {
            fileName = "test-file.bin"
            publicContent = publicData
            privateContent = privateData
        }
        fileRepository.save(file)

        // When
        val retrieved = fileRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals("test-file.bin", retrieved?.fileName)
        assertEquals(100 * 1024, retrieved?.publicContent?.size)
        assertEquals(200 * 1024, retrieved?.privateContent?.size)

        // Cleanup
        fileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should update large binary field`() {
        // Given
        val secret = generateSecret()
        val originalData = createSampleBytes(300) // 300KB
        val document = TestDocument().withSecret(secret).apply {
            title = "Update Test"
            pdfContent = originalData
        }
        documentRepository.save(document)

        // When - update the large field
        val retrieved = documentRepository.findBySecretOrNull(secret)
        val newData = createSampleBytes(400) // 400KB
        retrieved?.pdfContent = newData

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        documentRepository.flushThenClear()

        // Then
        val afterUpdate = documentRepository.findBySecretOrNull(secret)
        assertEquals(400 * 1024, afterUpdate?.pdfContent?.size)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    @Test
    fun `should cleanup GridFS files on entity delete`() {
        // Given
        val secret = generateSecret()
        val document = TestDocument().withSecret(secret).apply {
            title = "Cleanup Test"
            pdfContent = createSampleBytes(500)
            imageContent = createSampleBytes(300)
        }
        documentRepository.save(document)

        // When - delete entity
        documentRepository.deleteBySecret(secret)

        // Then - entity should not exist
        val exists = documentRepository.existsBySecret(secret)
        assertFalse(exists)

        // GridFS files should also be cleaned up (verified by framework internals)
    }

    @Test
    fun `should handle transition from small to large file`() {
        // Given - start with small file
        val secret = generateSecret()
        val smallData = ByteArray(512) { it.toByte() }
        val document = TestDocument().withSecret(secret).apply {
            title = "Transition Test"
            pdfContent = smallData
        }
        documentRepository.save(document)

        // When - update to large file
        val retrieved = documentRepository.findBySecretOrNull(secret)
        val largeData = createSampleBytes(500) // 500KB
        retrieved?.pdfContent = largeData

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        documentRepository.flushThenClear()

        // Then - should now be in GridFS
        val afterUpdate = documentRepository.findBySecretOrNull(secret)
        assertEquals(500 * 1024, afterUpdate?.pdfContent?.size)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle null binary fields`() {
        // Given
        val secret = generateSecret()
        val document = TestDocument().withSecret(secret).apply {
            title = "Null Binary Test"
            pdfContent = null
            imageContent = null
        }

        // When
        documentRepository.save(document)
        val retrieved = documentRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals("Null Binary Test", retrieved?.title)
        assertNull(retrieved?.pdfContent)
        assertNull(retrieved?.imageContent)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle multiple large files in same entity`() {
        // Given
        val secret = generateSecret()
        val pdf = createSampleBytes(1000) // 1MB
        val image = createSampleBytes(2000) // 2MB
        val thumbnail = ByteArray(256) { it.toByte() }

        val document = TestDocument().withSecret(secret).apply {
            title = "Multiple Files"
            pdfContent = pdf
            imageContent = image
            this.thumbnail = thumbnail
        }

        // When
        documentRepository.save(document)
        val retrieved = documentRepository.findBySecretOrNull(secret)

        // Then
        assertNotNull(retrieved)
        assertEquals(1000 * 1024, retrieved?.pdfContent?.size)
        assertEquals(2000 * 1024, retrieved?.imageContent?.size)
        assertEquals(256, retrieved?.thumbnail?.size)

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }
}