package wanion.encryptable.examples

import wanion.encryptable.util.extensions.markForWiping
import wanion.encryptable.util.HKDF
import tech.wanion.encryptable.mongo.Encryptable
import tech.wanion.encryptable.mongo.CID
import tech.wanion.encryptable.mongo.EncryptableMongoRepository
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository

/**
 * # Example 6: Derived Secrets & Secure Wiping
 *
 * This example demonstrates how to securely derive secrets from user credentials
 * (such as email, password, and 2FA secret), use them for cryptographic operations,
 * and mark all derived secrets and intermediate values for secure memory wiping.
 *
 * Best practices:
 * - Never persist user credentials or derived secrets in the database.
 * - Always mark all sensitive/intermediate values for wiping after use.
 * - Use HKDF or similar KDFs for deterministic secret derivation.
 */

 // ===== Entity Definition =====
@Document(collection = "users")
data class User(
    @Encrypt
    var email: String,
    @Encrypt
    var encryptedProfile: ByteArray
) : Encryptable<User>() {
    @HKDFId
    override var id: CID? = null
}

// ===== Repository Definition =====
@Repository
interface UserRepository : EncryptableMongoRepository<User>

// ===== Example Usage =====
class DerivedSecretsWipingExample(
    private val userRepository: UserRepository
) {
    /**
     * Generates a deterministic secret from user credentials using HKDF.
     */
    private fun deriveUserSecret(email: String, password: String, twoFASecret: String): String {
        val emailPassword = email + password
        val loginSecret = HKDF.deriveFromEntropy(emailPassword, "UserLogin", "LOGIN_SECRET")
        val twoFASecretDerived = HKDF.deriveFromEntropy(twoFASecret, "User2FA", "2FA_SECRET")
        // Combine login secret and 2FA secret to form the final user master secret
        // HKDF.driveFromEntropy already marks its inputs for wiping, but we mark all here for clarity.
        val loginAnd2FA = loginSecret + twoFASecretDerived.decodeToString()
        val userMasterSecret = HKDF.deriveFromEntropy(loginAnd2FA, "UserMaster", "MASTER_SECRET")
        val userMasterSecretString = userMasterSecret.decodeToString()
        // Mark all sensitive/intermediate values for secure wiping
        markForWiping(email, password, twoFASecret, emailPassword, loginSecret, twoFASecretDerived, loginAnd2FA, userMasterSecret, userMasterSecretString)
        return userMasterSecretString
    }

    /**
     * Simulates user creation and secret derivation.
     */
    fun createAndSaveUser(email: String, password: String, twoFASecret: String) {
        println("=== Example 6: Create and Save User with Derived Secret ===")
        // Derive secret and create user entity
        val secret = deriveUserSecret(email, password, twoFASecret)
        val user = User(
            email = email,
            encryptedProfile = HKDF.deriveFromEntropy("profile-data", "UserProfile", "PROFILE")
        ).withSecret(secret)

        // Save the user entity to the repository
        userRepository.save(user)
        println("User saved with ID: ${user.id}")
        println("Email (will be encrypted in DB): ${user.email}")
    }

    /**
     * Simulates user retrieval using the derived secret.
     */
    fun retrieveUser(email: String, password: String, twoFASecret: String) {
        println("\n=== Example 6: Retrieve User by Derived Secret ===")
        // Derive secret using provided credentials
        val secret = deriveUserSecret(email, password, twoFASecret)

        // Retrieve the user entity using the derived secret
        val user = userRepository.findBySecretOrNull(secret)
        if (user != null) {
            println("User found!")
            println("ID: ${user.id}")
            println("Email: ${user.email}")
            println("Encrypted profile: ${user.encryptedProfile?.take(8)}... (truncated)")
        } else {
            println("User not found - credentials or secret may be incorrect")
        }
    }
}

// ===== Example Runner =====
fun main() {
    // In a real app, userRepository would be injected by Spring
    // val userRepository: UserRepository = ...
    // val example = DerivedSecretsWipingExample(userRepository)
    // example.createAndSaveUser("alice@example.com", "SuperSecurePassword123!", "2fa-SECRET-xyz")
    // example.retrieveUser("alice@example.com", "SuperSecurePassword123!", "2fa-SECRET-xyz")
    println("This example requires a real UserRepository instance.")
}
