package cards.project.mongo

import cards.project.mongo.entity.TestUser
import cards.project.mongo.entity.TestUserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EncryptableSecretRotationTest : BaseEncryptableTest() {
    @Autowired
    lateinit var userRepository: TestUserRepository

    @Test
    fun `rotateSecret should update secret and allow access with new secret`() {
        // Arrange
        val oldSecret = generateSecret()
        val newSecret = generateSecret()
        val user = TestUser().apply {
            email = "rotate@example.com"
            firstName = "Rotate"
            lastName = "Secret"
        }.withSecret(oldSecret)
        userRepository.save(user)

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        userRepository.flushThenClear()

        assertTrue(userRepository.existsBySecret(oldSecret))
        assertNotNull(userRepository.findBySecretOrNull(oldSecret))

        // Act
        userRepository.rotateSecret(oldSecret, newSecret)

        // Assert
        assertFalse(userRepository.existsBySecret(oldSecret), "Old secret should not work after rotation")
        val found = userRepository.findBySecretOrNull(newSecret)
        assertNotNull(found, "User should be accessible with new secret after rotation")
        assertEquals("rotate@example.com", found?.email)
        assertEquals("Rotate", found?.firstName)
        assertEquals("Secret", found?.lastName)

        userRepository.deleteBySecret(newSecret)
    }
}