package cards.project.mongo

import cards.project.mongo.entity.TestFile
import cards.project.mongo.entity.TestFileRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EncryptableGridFSRotationTest : BaseEncryptableTest() {
    @Autowired
    lateinit var fileRepository: TestFileRepository

    @Test
    fun `rotateSecret should work for entities with GridFS files larger than 1KB`() {
        // Arrange
        val oldSecret = generateSecret()
        val newSecret = generateSecret()
        val fileName = "largefile.bin"
        val publicContent = ByteArray(2048) { 0x42 }
        val privateContent = ByteArray(2048) { 0x24 }
        val file = TestFile().apply {
            this.fileName = fileName
            this.publicContent = publicContent.copyOf()
            this.privateContent = privateContent.copyOf()
        }.withSecret(oldSecret)

        fileRepository.save(file)

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        fileRepository.flushThenClear()

        // Confirm file is accessible with old secret
        val foundOld = fileRepository.findBySecretOrNull(oldSecret)
        assertNotNull(foundOld)
        assertEquals(fileName, foundOld?.fileName)
        assertArrayEquals(publicContent, foundOld?.publicContent)
        assertArrayEquals(privateContent, foundOld?.privateContent)

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        fileRepository.flushThenClear()

        // Act: rotate secret
        fileRepository.rotateSecret(oldSecret, newSecret)

        // Assert: not accessible with old secret
        assertNull(fileRepository.findBySecretOrNull(oldSecret))
        // Assert: accessible with new secret and data is intact
        val foundNew = fileRepository.findBySecretOrNull(newSecret)
        assertNotNull(foundNew)
        assertEquals(fileName, foundNew?.fileName)
        assertArrayEquals(publicContent, foundNew?.publicContent)
        assertArrayEquals(privateContent, foundNew?.privateContent)

        fileRepository.deleteBySecret(newSecret)
    }
}