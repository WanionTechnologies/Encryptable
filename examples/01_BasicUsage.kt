package wanion.encryptable

import wanion.encryptable.mongo.CID
import wanion.encryptable.mongo.Encryptable
import wanion.encryptable.mongo.EncryptableMongoRepository
import wanion.encryptable.mongo.Encrypt
import wanion.encryptable.mongo.HKDFId
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import java.security.SecureRandom
import java.util.Base64

/**
 * # Example 1: Basic Usage
 *
 * This example demonstrates the fundamental usage of the Encryptable framework:
 * - Creating an entity with encrypted fields
 * - Saving and retrieving entities using secrets
 * - Working with encrypted strings
 */

// ===== Entity Definition =====

@Document(collection = "users")
class User : Encryptable<User>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var email: String? = null

    @Encrypt
    var firstName: String? = null

    @Encrypt
    var lastName: String? = null

    var publicUsername: String? = null // Not encrypted - visible to all

    @Encrypt
    var phoneNumber: String? = null
}

// ===== Repository Definition =====

@Repository
interface UserRepository : EncryptableMongoRepository<User>

// ===== Example Usage =====

class BasicUsageExample(
    private val userRepository: UserRepository
) {
    /**
     * Generates a cryptographically secure secret with proper entropy
     * Best practice: Use at least 256 bits (32 bytes) of entropy
     */
    private fun generateSecret(): String = String.randomSecret()

    fun example1_CreateAndSaveUser() {
        println("=== Example 1: Create and Save User ===")

        // Generate a high-entropy secret
        val secret = generateSecret()
        println("Generated secret: ${secret.take(10)}... (truncated)")

        // Create a new user with encrypted fields
        val user = User().withSecret(secret).apply {
            email = "alice@example.com"
            firstName = "Alice"
            lastName = "Smith"
            publicUsername = "alice_smith" // Not encrypted
            phoneNumber = "+1-555-0123"
        }

        // Save to MongoDB - automatically encrypts fields and sets deterministic ID
        userRepository.save(user, secret)
        println("User saved with ID: ${user.id}")
        println("Email (will be encrypted in DB): ${user.email}")
        println("Public username (not encrypted): ${user.publicUsername}")

        // The secret should be stored securely (e.g., user's session, secure vault)
        // Never persist the secret in the database!
    }

    fun example2_RetrieveUser(secret: String) {
        println("\n=== Example 2: Retrieve User by Secret ===")

        // Retrieve using the secret - automatically decrypts fields
        val user = userRepository.findBySecretOrNull(secret)

        if (user != null) {
            println("User found!")
            println("ID: ${user.id}")
            println("Email: ${user.email}")
            println("Name: ${user.firstName} ${user.lastName}")
            println("Phone: ${user.phoneNumber}")
            println("Username: ${user.publicUsername}")
        } else {
            println("User not found - secret may be incorrect")
        }
    }

    fun example3_AlternativeRetrievalSyntax(secret: String) {
        println("\n=== Example 3: Alternative Retrieval Syntax ===")

        // Kotlin extension function for more idiomatic syntax
        val user = secret.asSecretOf<User>()

        if (user != null) {
            println("Retrieved using extension function: ${user.email}")
        }
    }

    fun example4_CheckExistence(secret: String) {
        println("\n=== Example 4: Check if User Exists ===")

        val exists = userRepository.existsBySecret(secret)
        println("User exists: $exists")
    }

    fun example5_UpdateUser(secret: String) {
        println("\n=== Example 5: Update User Fields ===")

        // Retrieve the user
        val user = userRepository.findBySecretOrNull(secret)

        if (user != null) {
            println("Before update - Email: ${user.email}")

            // Modify fields - changes are tracked automatically
            user.email = "alice.smith@newdomain.com"
            user.phoneNumber = "+1-555-9999"

            // When the request completes, only changed fields are updated in MongoDB
            // No need to call save() explicitly - the framework handles it!
            println("After update - Email: ${user.email}")
            println("Note: Changes will be persisted automatically when request completes")

            // If you need to force an immediate update, you can call:
            // userRepository.clear() // This triggers the update for all tracked entities
        }
    }

    fun example6_DeleteUser(secret: String) {
        println("\n=== Example 6: Delete User ===")

        // Delete by secret
        userRepository.deleteBySecret(secret)
        println("User deleted")

        // Verify deletion
        val exists = userRepository.existsBySecret(secret)
        println("User still exists: $exists")
    }

    fun example7_BatchOperations() {
        println("\n=== Example 7: Batch Operations ===")

        // Create multiple users
        val secrets = (1..3).map { generateSecret() }

        val users = secrets.mapIndexed { index, secret ->
            User().withSecret(secret).apply {
                email = "user$index@example.com"
                firstName = "User"
                lastName = "Number$index"
                publicUsername = "user$index"
            }
        }

        // Save all users
        userRepository.saveAll(users)
        println("Saved ${users.size} users")

        // Retrieve multiple users by secrets
        val retrieved = userRepository.findAllBySecrets(secrets)
        println("Retrieved ${retrieved.size} users:")
        retrieved.forEach { user ->
            println("  - ${user.email}")
        }

        // Delete all users
        userRepository.deleteAllBySecrets(secrets)
        println("Deleted all users")
    }

    fun runAllExamples() {
        val secret = generateSecret()

        example1_CreateAndSaveUser()
        example2_RetrieveUser(secret)
        example3_AlternativeRetrievalSyntax(secret)
        example4_CheckExistence(secret)
        example5_UpdateUser(secret)
        // example6_DeleteUser(secret) // Commented to preserve data for other examples
        example7_BatchOperations()

        println("\n=== All examples completed ===")
    }
}
