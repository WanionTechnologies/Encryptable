 package wanion.encryptable

import wanion.encryptable.mongo.CID
import wanion.encryptable.mongo.Encryptable
import wanion.encryptable.mongo.EncryptableMongoRepository
import wanion.encryptable.mongo.Encrypt
import wanion.encryptable.mongo.HKDFId
import wanion.encryptable.mongo.annotation.PartOf
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * # Example 5: Advanced Patterns and Best Practices
 *
 * This example demonstrates:
 * - Secret generation best practices
 * - Error handling and validation
 * - Performance optimization techniques
 * - Audit logging patterns
 * - Multi-tenant patterns
 * - Complex nested structures
 */

// ===== Advanced Entity Definitions =====

@Document(collection = "audit_logs")
class AuditLog : Encryptable<AuditLog>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var userId: String? = null

    @Encrypt
    var action: String? = null

    @Encrypt
    var details: String? = null

    var timestamp: Instant = Instant.now()
    var ipAddress: String? = null

    // Override touch() to update last access time
    var lastAccessedAt: Instant? = null

    override fun touch() {
        lastAccessedAt = Instant.now()
        println("Audit log accessed at: $lastAccessedAt")
    }
}

@Document(collection = "medical_records")
class MedicalRecord : Encryptable<MedicalRecord>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var patientId: String? = null

    @Encrypt
    var diagnosis: String? = null

    @Encrypt
    var prescription: String? = null

    @Encrypt
    var notes: String? = null

    @Encrypt
    var labResults: ByteArray? = null

    var recordDate: Instant = Instant.now()
    var doctorId: String? = null // Not encrypted - used for queries
}

@Document(collection = "organizations")
class Organization : Encryptable<Organization>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var organizationName: String? = null

    @Encrypt
    @PartOf
    var departments: MutableList<Department> = mutableListOf()

    var isActive: Boolean = true
}

@Document(collection = "departments")
class Department : Encryptable<Department>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var departmentName: String? = null

    @Encrypt
    @PartOf
    var employees: MutableList<Employee> = mutableListOf()
}

@Document(collection = "employees")
class Employee : Encryptable<Employee>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var fullName: String? = null

    @Encrypt
    var email: String? = null

    @Encrypt
    var salary: Double? = null
}

// ===== Repository Definitions =====

@Repository
interface AuditLogRepository : EncryptableMongoRepository<AuditLog>

@Repository
interface MedicalRecordRepository : EncryptableMongoRepository<MedicalRecord>

@Repository
interface OrganizationRepository : EncryptableMongoRepository<Organization>

// ===== Example Usage =====

class AdvancedPatternsExample(
    private val auditLogRepository: AuditLogRepository,
    private val medicalRecordRepository: MedicalRecordRepository,
    private val organizationRepository: OrganizationRepository
) {

    // ===== Best Practice: Secure Secret Generation =====

    /**
     * Generates a cryptographically secure secret with proper entropy
     * Best practice: Use at least 256 bits (32 bytes) of entropy
     */
    private fun generateSecret(): String = String.randomSecret()

    /**
     * Derives a deterministic secret from user credentials (e.g., password)
     * WARNING: Only for demonstration - in production use proper KDF like Argon2
     * Note: Do NOT use URL-safe encoding for password-derived secrets, as these are for cryptographic use, not resource addressing.
     */
    private fun deriveSecretFromPassword(password: String, salt: ByteArray): String {
        // This is simplified for demonstration
        val combined = password.toByteArray() + salt
        // Use standard Base64 encoding (not URL-safe)
        return Base64.getEncoder().withoutPadding().encodeToString(combined.copyOf(32))
    }

    fun example1_SecretManagement() {
        println("=== Example 1: Secret Generation Best Practices ===")

        // Good: High-entropy random secret
        val randomSecret = generateSecureSecret()
        println("Random secret (256-bit): ${randomSecret.take(20)}...")
        println("Length: ${randomSecret.length} characters")
        println("Entropy: ~256 bits")

        // Good for specific use cases: Derived from user password
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derivedSecret = deriveSecretFromPassword("user_password_here", salt)
        println("\nDerived secret: ${derivedSecret.take(20)}...")
        println("Use case: Tie entity access to user authentication")

        println("\n❌ BAD PRACTICES TO AVOID:")
        println("- Using user IDs directly as secrets")
        println("- Using sequential numbers")
        println("- Using predictable values (timestamps, etc.)")
        println("- Secrets with <128 bits of entropy")

        println("\n✅ BEST PRACTICES:")
        println("- Generate 256+ bit random secrets")
        println("- Use SecureRandom, not Random")
        println("- Store secrets in secure vault (HashiCorp Vault, AWS KMS)")
        println("- Never log or expose secrets in errors")
        println("- Implement secret rotation policies")
    }

    fun example2_ErrorHandlingAndValidation() {
        println("\n=== Example 2: Error Handling and Validation ===")

        val secret = generateSecureSecret()

        try {
            // Validation before creating entity
            val patientId = "PATIENT-12345"
            require(patientId.isNotBlank()) { "Patient ID cannot be blank" }

            val record = MedicalRecord().withSecret(secret).apply {
                this.patientId = patientId
                diagnosis = "Example diagnosis"
                prescription = "Example prescription"
                notes = "Patient notes here"
                doctorId = "DR-001"
            }

            medicalRecordRepository.save(record, secret)
            println("✅ Medical record saved successfully")
            println("Record ID: ${record.id}")

        } catch (e: IllegalArgumentException) {
            println("❌ Validation error: ${e.message}")
        } catch (e: Exception) {
            println("❌ Save failed: ${e.message}")
            // Log error, notify monitoring system
            // DO NOT expose secret in logs!
        }

        // Retrieving with error handling
        try {
            val retrieved = medicalRecordRepository.findBySecretOrNull(secret)
            if (retrieved != null) {
                println("✅ Record retrieved: Patient ${retrieved.patientId}")
            } else {
                println("⚠️ No record found (wrong secret or doesn't exist)")
            }
        } catch (e: Exception) {
            println("❌ Retrieval failed: ${e.message}")
        }

        // Cleanup
        medicalRecordRepository.deleteBySecret(secret)
    }

    fun example3_AuditLoggingPattern() {
        println("\n=== Example 3: Audit Logging with touch() ===")

        val secret = generateSecureSecret()
        val auditLog = AuditLog().withSecret(secret).apply {
            userId = "user123"
            action = "LOGIN"
            details = "User logged in from mobile app"
            ipAddress = "192.168.1.100"
        }

        auditLogRepository.save(auditLog, secret)
        println("Audit log created at: ${auditLog.timestamp}")
        println("Last accessed: ${auditLog.lastAccessedAt}")

        // Retrieve the log - touch() is called automatically
        println("\nRetrieving audit log...")
        val retrieved = auditLogRepository.findBySecretOrNull(secret)

        if (retrieved != null) {
            println("Log retrieved - touch() was called")
            println("Last accessed updated to: ${retrieved.lastAccessedAt}")
            println("\nUse touch() to implement:")
            println("- Last access timestamps")
            println("- Access counters")
            println("- Audit trails")
            println("- Rate limiting checks")
        }

        // Cleanup
        auditLogRepository.deleteBySecret(secret)
    }

    fun example4_ComplexNestedStructures() {
        println("\n=== Example 4: Complex Nested Structures ===")

        // Create employees
        val emp1Secret = generateSecureSecret()
        val employee1 = Employee().withSecret(emp1Secret).apply {
            fullName = "Alice Johnson"
            email = "alice@company.com"
            salary = 75000.0
        }

        val emp2Secret = generateSecureSecret()
        val employee2 = Employee().withSecret(emp2Secret).apply {
            fullName = "Bob Smith"
            email = "bob@company.com"
            salary = 82000.0
        }

        // Create department with employees
        val dept1Secret = generateSecureSecret()
        val department1 = Department().withSecret(dept1Secret).apply {
            departmentName = "Engineering"
            employees = mutableListOf(employee1, employee2)
        }

        val emp3Secret = generateSecureSecret()
        val employee3 = Employee().withSecret(emp3Secret).apply {
            fullName = "Charlie Brown"
            email = "charlie@company.com"
            salary = 68000.0
        }

        val dept2Secret = generateSecureSecret()
        val department2 = Department().withSecret(dept2Secret).apply {
            departmentName = "Marketing"
            employees = mutableListOf(employee3)
        }

        // Create organization with departments
        val orgSecret = generateSecureSecret()
        val organization = Organization().withSecret(orgSecret).apply {
            organizationName = "Tech Corp"
            departments = mutableListOf(department1, department2)
            isActive = true
        }

        organizationRepository.save(organization, orgSecret)
        println("Organization created: ${organization.organizationName}")
        println("Departments: ${organization.departments.size}")

        organization.departments.forEach { dept ->
            println("\n  Department: ${dept.departmentName}")
            println("  Employees: ${dept.employees.size}")
            dept.employees.forEach { emp ->
                println("    - ${emp.fullName} (${emp.email})")
            }
        }

        println("\nCascade delete behavior:")
        println("Deleting organization will cascade delete:")
        println("  ✓ All departments (marked @PartOf)")
        println("  ✓ All employees in those departments (marked @PartOf)")

        // Cleanup - this will cascade delete everything
        organizationRepository.deleteBySecret(orgSecret)
        println("\nOrganization deleted (cascade deleted all nested entities)")
    }

    fun example5_PartialUpdateOptimization() {
        println("\n=== Example 5: Partial Update Optimization ===")

        val secret = generateSecureSecret()
        val record = MedicalRecord().withSecret(secret).apply {
            patientId = "PATIENT-999"
            diagnosis = "Initial diagnosis"
            prescription = "Initial prescription"
            notes = "Initial notes"
            doctorId = "DR-123"
        }

        medicalRecordRepository.save(record, secret)
        println("Medical record created")

        // Retrieve and modify only one field
        val retrieved = medicalRecordRepository.findBySecretOrNull(secret)
        if (retrieved != null) {
            println("\nModifying only the 'notes' field...")
            retrieved.notes = "Updated notes with new information"

            println("\nFramework behavior:")
            println("1. Change detection: Compares field-level hashes")
            println("2. Identifies that only 'notes' changed")
            println("3. Re-encrypts only the 'notes' field")
            println("4. Issues MongoDB update for 'notes' only")
            println("5. Other fields (diagnosis, prescription) not touched")

            println("\nBenefit: Reduced write amplification and better concurrency")
        }

        // Cleanup
        medicalRecordRepository.deleteBySecret(secret)
    }

    fun example6_MultiTenantPattern() {
        println("\n=== Example 6: Multi-Tenant Pattern ===")

        // Each tenant has their own secret
        val tenant1Secret = generateSecureSecret()
        val tenant2Secret = generateSecureSecret()

        // Tenant 1's data
        val tenant1Record = MedicalRecord().withSecret(tenant1Secret).apply {
            patientId = "TENANT1-PATIENT-001"
            diagnosis = "Tenant 1 diagnosis"
            notes = "Confidential to Tenant 1"
            doctorId = "TENANT1-DR-001"
        }
        medicalRecordRepository.save(tenant1Record, tenant1Secret)

        // Tenant 2's data
        val tenant2Record = MedicalRecord().withSecret(tenant2Secret).apply {
            patientId = "TENANT2-PATIENT-001"
            diagnosis = "Tenant 2 diagnosis"
            notes = "Confidential to Tenant 2"
            doctorId = "TENANT2-DR-001"
        }
        medicalRecordRepository.save(tenant2Record, tenant2Secret)

        println("Multi-tenant isolation:")
        println("- Each tenant's data encrypted with different secret")
        println("- Tenant 1 cannot decrypt Tenant 2's data (and vice versa)")
        println("- Deterministic IDs prevent ID collisions across tenants")
        println("- Secret acts as both identifier and encryption key")

        // Tenant 1 can only access their data
        val tenant1Data = medicalRecordRepository.findBySecretOrNull(tenant1Secret)
        println("\nTenant 1 access: ${tenant1Data?.patientId}")

        // Tenant 2 cannot access Tenant 1's data with their secret
        val wrongAccess = medicalRecordRepository.findBySecretOrNull(tenant2Secret)
        println("Tenant 2 trying Tenant 1's ID: ${wrongAccess?.patientId != tenant1Data?.patientId}")

        // Cleanup
        medicalRecordRepository.deleteBySecret(tenant1Secret)
        medicalRecordRepository.deleteBySecret(tenant2Secret)
    }

    fun example7_PerformanceBestPractices() {
        println("\n=== Example 7: Performance Best Practices ===")

        println("✅ DO:")
        println("1. Use batch operations (saveAll, findAllBySecrets)")
        println("2. Only access large ByteArray fields when needed (lazy loading)")
        println("3. Leverage parallel processing (automatic in framework)")
        println("4. Keep documents under 16MB BSON limit")
        println("5. Use GridFS for files >1KB")
        println("6. Implement pagination for large result sets")

        println("\n❌ DON'T:")
        println("1. Load all entities at once (use pagination)")
        println("2. Access large files unnecessarily")
        println("3. Create circular references")
        println("4. Store secrets in logs or error messages")
        println("5. Use predictable secrets")

        println("\n💡 OPTIMIZATION TIPS:")
        println("- Metadata caching is automatic (thread-safe)")
        println("- Field-level change detection = minimal DB writes")
        println("- Parallel encryption/decryption for multiple fields")
        println("- Only changed fields are updated in MongoDB")
    }

    fun runAllExamples() {
        example1_SecretManagement()
        example2_ErrorHandlingAndValidation()
        example3_AuditLoggingPattern()
        example4_ComplexNestedStructures()
        example5_PartialUpdateOptimization()
        example6_MultiTenantPattern()
        example7_PerformanceBestPractices()

        println("\n=== Advanced Patterns Examples Completed ===")
    }
}
