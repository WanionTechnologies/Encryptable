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

/**
 * # Example 2: Nested Entities and Relationships
 *
 * This example demonstrates:
 * - Creating nested Encryptable entities
 * - Using @PartOf for composition relationships
 * - Cascade deletes
 * - Encrypting complex object graphs
 */

// ===== Entity Definitions =====

@Document(collection = "addresses")
class Address : Encryptable<Address>() {
    @HKDFId
    override var id: UUID? = null

    @Encrypt
    var street: String? = null

    @Encrypt
    var city: String? = null

    @Encrypt
    var state: String? = null

    @Encrypt
    var zipCode: String? = null

    @Encrypt
    var country: String? = null
}

@Document(collection = "customers")
class Customer : Encryptable<Customer>() {
    @HKDFId
    override var id: UUID? = null

    @Encrypt
    var name: String? = null

    @Encrypt
    var email: String? = null

    // Nested Encryptable with @PartOf - will be deleted when customer is deleted
    @Encrypt
    @PartOf
    var billingAddress: Address? = null

    // Another nested entity
    @Encrypt
    @PartOf
    var shippingAddress: Address? = null

    var accountCreatedAt: String? = null // Not encrypted
}

@Document(collection = "companies")
class Company : Encryptable<Company>() {
    @HKDFId
    override var id: UUID? = null

    @Encrypt
    var companyName: String? = null

    // Reference without @PartOf - address won't be deleted when company is deleted
    @Encrypt
    var headquarters: Address? = null
}

// ===== Repository Definitions =====

@Repository
interface CustomerRepository : EncryptableMongoRepository<Customer, UUID>

@Repository
interface AddressRepository : EncryptableMongoRepository<Address, UUID>

@Repository
interface CompanyRepository : EncryptableMongoRepository<Company, UUID>

// ===== Example Usage =====

class NestedEntitiesExample(
    private val customerRepository: CustomerRepository,
    private val addressRepository: AddressRepository,
    private val companyRepository: CompanyRepository
) {
    /**
     * Generates a cryptographically secure secret with proper entropy
     * Best practice: Use at least 256 bits (32 bytes) of entropy
     */
    private fun generateSecret(): String = String.randomSecret()

    fun example1_CreateNestedEntities() {
        println("=== Example 1: Create Customer with Nested Addresses ===")

        // Create billing address with its own secret
        val billingSecret = generateSecret()
        val billingAddress = Address().withSecret(billingSecret).apply {
            street = "123 Main St"
            city = "Springfield"
            state = "IL"
            zipCode = "62701"
            country = "USA"
        }
        addressRepository.save(billingAddress, billingSecret)
        println("Billing address saved with ID: ${billingAddress.id}")

        // Create shipping address with its own secret
        val shippingSecret = generateSecret()
        val shippingAddress = Address().withSecret(shippingSecret).apply {
            street = "456 Oak Ave"
            city = "Portland"
            state = "OR"
            zipCode = "97201"
            country = "USA"
        }
        addressRepository.save(shippingAddress, shippingSecret)
        println("Shipping address saved with ID: ${shippingAddress.id}")

        // Create customer referencing the addresses
        val customerSecret = generateSecret()
        val customer = Customer().withSecret(customerSecret).apply {
            name = "John Doe"
            email = "john.doe@example.com"
            billingAddress = billingAddress
            shippingAddress = shippingAddress
            accountCreatedAt = java.time.Instant.now().toString()
        }
        customerRepository.save(customer, customerSecret)
        println("Customer saved with ID: ${customer.id}")
        println("Customer has ${if (customer.billingAddress != null) "billing" else "no billing"} address")
        println("Customer has ${if (customer.shippingAddress != null) "shipping" else "no shipping"} address")

        // Store secrets securely for later retrieval
        println("\nSecrets to store securely:")
        println("Customer secret: ${customerSecret.take(20)}...")
        println("Billing address secret: ${billingSecret.take(20)}...")
        println("Shipping address secret: ${shippingSecret.take(20)}...")
    }

    fun example2_RetrieveNestedEntities(customerSecret: String) {
        println("\n=== Example 2: Retrieve Customer with Nested Addresses ===")

        val customer = customerRepository.findBySecretOrNull(customerSecret)

        if (customer != null) {
            println("Customer: ${customer.name}")
            println("Email: ${customer.email}")

            // Nested addresses are automatically decrypted
            customer.billingAddress?.let { billing ->
                println("\nBilling Address:")
                println("  ${billing.street}")
                println("  ${billing.city}, ${billing.state} ${billing.zipCode}")
                println("  ${billing.country}")
            }

            customer.shippingAddress?.let { shipping ->
                println("\nShipping Address:")
                println("  ${shipping.street}")
                println("  ${shipping.city}, ${shipping.state} ${shipping.zipCode}")
                println("  ${shipping.country}")
            }
        }
    }

    fun example3_CascadeDelete(customerSecret: String) {
        println("\n=== Example 3: Cascade Delete with @PartOf ===")

        // First, verify the customer and addresses exist
        val customer = customerRepository.findBySecretOrNull(customerSecret)
        val billingId = customer?.billingAddress?.id
        val shippingId = customer?.shippingAddress?.id

        println("Before delete:")
        println("Customer exists: ${customer != null}")
        println("Billing address ID: $billingId")
        println("Shipping address ID: $shippingId")

        // Delete the customer - this will CASCADE delete the addresses
        // because they are marked with @PartOf
        customerRepository.deleteBySecret(customerSecret)
        println("\nCustomer deleted (cascade delete triggered)")

        // Verify addresses were also deleted (if you had their secrets)
        val customerStillExists = customerRepository.existsBySecret(customerSecret)
        println("\nAfter delete:")
        println("Customer still exists: $customerStillExists")
        println("Note: Billing and shipping addresses were also deleted due to @PartOf annotation")
    }

    fun example4_ReferenceWithoutPartOf() {
        println("\n=== Example 4: Reference without @PartOf (Shared Entity) ===")

        // Create a shared address
        val addressSecret = generateSecret()
        val headquarters = Address().withSecret(addressSecret).apply {
            street = "789 Corporate Blvd"
            city = "New York"
            state = "NY"
            zipCode = "10001"
            country = "USA"
        }
        addressRepository.save(headquarters, addressSecret)
        println("Headquarters address saved with ID: ${headquarters.id}")

        // Create a company referencing the address (without @PartOf)
        val companySecret = generateSecret()
        val company = Company().withSecret(companySecret).apply {
            companyName = "Acme Corporation"
            this.headquarters = headquarters
        }
        companyRepository.save(company, companySecret)
        println("Company saved with ID: ${company.id}")

        // Delete the company
        companyRepository.deleteBySecret(companySecret)
        println("Company deleted")

        // The headquarters address still exists because it's NOT marked with @PartOf
        val addressStillExists = addressRepository.existsBySecret(addressSecret)
        println("Headquarters address still exists: $addressStillExists")
        println("This is because the 'headquarters' field is NOT annotated with @PartOf")

        // Cleanup
        addressRepository.deleteBySecret(addressSecret)
        println("Headquarters address manually deleted")
    }

    fun example5_UpdateNestedEntity(customerSecret: String) {
        println("\n=== Example 5: Update Nested Entity ===")

        val customer = customerRepository.findBySecretOrNull(customerSecret)

        if (customer != null) {
            println("Original billing address: ${customer.billingAddress?.street}")

            // Modify the nested address
            customer.billingAddress?.apply {
                street = "999 Updated Street"
                city = "New City"
            }

            println("Updated billing address: ${customer.billingAddress?.street}")
            println("Changes will be persisted automatically when request completes")

            // The framework will detect the change and update only the modified fields
        }
    }

    fun runAllExamples() {
        // Example 1: Create with nested entities
        example1_CreateNestedEntities()

        // For other examples, you would need to pass the actual secrets
        // In a real application, these would be retrieved from secure storage

        println("\n=== Nested Entities Examples Completed ===")
        println("Note: Examples 2-5 require passing actual secrets from example 1")
    }
}
