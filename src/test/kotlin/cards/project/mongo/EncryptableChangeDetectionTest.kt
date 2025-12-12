package cards.project.mongo

import cards.project.mongo.entity.TestAuditEntity
import cards.project.mongo.entity.TestAuditEntityRepository
import cards.project.mongo.entity.TestProfile
import cards.project.mongo.entity.TestProfileRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for field-level change detection and partial updates
 * Verifies that only modified fields are updated in MongoDB
 */
class EncryptableChangeDetectionTest() : BaseEncryptableTest() {

    @Autowired
    private lateinit var profileRepository: TestProfileRepository

    @Autowired
    private lateinit var auditRepository: TestAuditEntityRepository

    @Test
    fun `should detect single field change`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "john_doe"
            email = "john@example.com"
            bio = "Software developer"
        }
        profileRepository.save(profile)

        // When - change only email
        val retrieved = profileRepository.findBySecretOrNull(secret)
        val originalUsername = retrieved?.username
        val originalBio = retrieved?.bio

        retrieved?.email = "john.doe@newdomain.com"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - only email should be updated
        val afterUpdate = profileRepository.findBySecretOrNull(secret)
        assertEquals(originalUsername, afterUpdate?.username, "Username should not change")
        assertEquals("john.doe@newdomain.com", afterUpdate?.email, "Email should be updated")
        assertEquals(originalBio, afterUpdate?.bio, "Bio should not change")

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should detect multiple field changes`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "jane_smith"
            email = "jane@example.com"
            bio = "Designer"
        }
        profileRepository.save(profile)

        // When - change multiple fields
        val retrieved = profileRepository.findBySecretOrNull(secret)
        retrieved?.email = "jane.smith@updated.com"
        retrieved?.bio = "Senior Designer"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - both fields should be updated
        val afterUpdate = profileRepository.findBySecretOrNull(secret)
        assertEquals("jane_smith", afterUpdate?.username, "Username unchanged")
        assertEquals("jane.smith@updated.com", afterUpdate?.email, "Email updated")
        assertEquals("Senior Designer", afterUpdate?.bio, "Bio updated")

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should not update when no fields changed`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "no_change"
            email = "nochange@example.com"
        }
        profileRepository.save(profile)

        // When - retrieve but don't modify
        val retrieved = profileRepository.findBySecretOrNull(secret)
        val originalId = retrieved?.id

        // Then - no update should occur
        val afterClear = profileRepository.findBySecretOrNull(secret)
        assertEquals(originalId, afterClear?.id)
        assertEquals("no_change", afterClear?.username)
        assertEquals("nochange@example.com", afterClear?.email)

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle binary field changes`() {
        // Given
        val secret = generateSecret()
        val originalAvatar = ByteArray(100) { it.toByte() }
        val profile = TestProfile().withSecret(secret).apply {
            username = "avatar_test"
            avatar = originalAvatar
        }
        profileRepository.save(profile)

        // When - change avatar
        val retrieved = profileRepository.findBySecretOrNull(secret)
        val newAvatar = ByteArray(150) { (it * 2).toByte() }
        retrieved?.avatar = newAvatar

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - avatar should be updated
        val afterUpdate = profileRepository.findBySecretOrNull(secret)
        assertEquals(150, afterUpdate?.avatar?.size)
        assertArrayEquals(newAvatar, afterUpdate?.avatar)

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should compute field hashes correctly`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "hash_test"
            email = "hash@test.com"
            bio = "Testing hash computation"
        }
        profileRepository.save(profile)

        // When - retrieve and get hash codes
        val retrieved = profileRepository.findBySecretOrNull(secret)
        val hashCodes = retrieved?.hashCodes()

        // Then - hash codes should exist for persisted fields
        assertNotNull(hashCodes)
        assertTrue(hashCodes?.containsKey("username") == true)
        assertTrue(hashCodes?.containsKey("email") == true)
        assertTrue(hashCodes?.containsKey("bio") == true)

        // Verify hash changes when field changes
        val originalEmailHash = hashCodes?.get("email")
        retrieved?.email = "newhash@test.com"
        val newHashCodes = retrieved?.hashCodes()
        val newEmailHash = newHashCodes?.get("email")

        assertNotEquals(originalEmailHash, newEmailHash, "Hash should change when field changes")

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should use touch() for audit fields`() {
        // Given
        val secret = generateSecret()
        val entity = TestAuditEntity().withSecret(secret).apply {
            data = "Test data"
        }
        auditRepository.save(entity)

        // When - retrieve entity (touch() called automatically)
        val retrieved = auditRepository.findBySecretOrNull(secret)

        // Then - touch() should have updated audit fields
        assertEquals(1, retrieved?.accessCount, "Access count should be incremented by touch()")
        assertNotNull(retrieved?.lastAccessedAt, "Last accessed should be set by touch()")

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        auditRepository.flushThenClear()

        // When - retrieve again
        val retrievedAgain = auditRepository.findBySecretOrNull(secret)

        // Then - access count incremented again
        assertEquals(2, retrievedAgain?.accessCount, "Access count should increment on each retrieval")

        // Cleanup
        auditRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle setting field to null`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "null_test"
            email = "original@example.com"
            bio = "Original bio"
        }
        profileRepository.save(profile)

        // When - set field to null
        val retrieved = profileRepository.findBySecretOrNull(secret)
        retrieved?.bio = null

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - field should be null
        val afterUpdate = profileRepository.findBySecretOrNull(secret)
        assertEquals("null_test", afterUpdate?.username)
        assertEquals("original@example.com", afterUpdate?.email)
        assertNull(afterUpdate?.bio, "Bio should be null")

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle changing null to value`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "null_to_value"
            email = null
            bio = null
        }
        profileRepository.save(profile)

        // When - set null fields to values
        val retrieved = profileRepository.findBySecretOrNull(secret)
        retrieved?.email = "new@example.com"
        retrieved?.bio = "New bio"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - fields should have values
        val afterUpdate = profileRepository.findBySecretOrNull(secret)
        assertEquals("new@example.com", afterUpdate?.email)
        assertEquals("New bio", afterUpdate?.bio)

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should handle large binary field changes efficiently`() {
        // Given - binary data under GridFS threshold to test change detection
        val secret = generateSecret()
        val largeBinary = ByteArray(800) { (it % 256).toByte() } // 800 bytes - under GridFS threshold
        val profile = TestProfile().withSecret(secret).apply {
            username = "large_binary"
            avatar = largeBinary
        }
        profileRepository.save(profile)

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // When - modify only username (not the binary)
        val retrieved = profileRepository.findBySecretOrNull(secret)
        assertNotNull(retrieved, "Profile should be retrieved")
        assertEquals(800, retrieved?.avatar?.size, "Avatar should be saved correctly")

        retrieved?.username = "large_binary_updated"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - only username should be re-encrypted, not the binary
        val afterUpdate = profileRepository.findBySecretOrNull(secret)
        assertEquals("large_binary_updated", afterUpdate?.username)
        assertEquals(800, afterUpdate?.avatar?.size, "Avatar size should remain unchanged")

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }

    @Test
    fun `should track changes across multiple retrievals`() {
        // Given
        val secret = generateSecret()
        val profile = TestProfile().withSecret(secret).apply {
            username = "multi_retrieval"
            email = "multi@example.com"
        }
        profileRepository.save(profile)

        // When - first retrieval and modification
        val first = profileRepository.findBySecretOrNull(secret)
        first?.email = "first@example.com"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Second retrieval and modification
        val second = profileRepository.findBySecretOrNull(secret)
        assertEquals("first@example.com", second?.email, "First change should persist")
        second?.email = "second@example.com"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        profileRepository.flushThenClear()

        // Then - final verification
        val final = profileRepository.findBySecretOrNull(secret)
        assertEquals("second@example.com", final?.email, "Second change should persist")

        // Cleanup
        profileRepository.deleteBySecret(secret)
    }
}