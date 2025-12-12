package cards.project.mongo

import cards.project.mongo.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for nested entities and @PartOf cascade delete behavior
 */
class EncryptableNestedEntitiesTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var addressRepository: TestAddressRepository

    @Autowired
    private lateinit var customerRepository: TestCustomerRepository

    @Autowired
    private lateinit var companyRepository: TestCompanyRepository

    @Test
    fun `should save and retrieve nested entities`() {
        // Given
        val billingSecret = generateSecret()
        val billingAddress = TestAddress().withSecret(billingSecret).apply {
            street = "123 Main St"
            city = "Springfield"
            zipCode = "12345"
        }
        addressRepository.save(billingAddress)

        // Retrieve fresh instance after saving to initialize encryption context
        val freshBillingAddress = addressRepository.findBySecretOrNull(billingSecret)!!

        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "John Doe"
            this.billingAddress = freshBillingAddress
        }
        customerRepository.save(customer)


        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        customerRepository.flushThenClear()

        // When
        val retrieved = customerRepository.findBySecretOrNull(customerSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("John Doe", retrieved?.name)
        assertNotNull(retrieved?.billingAddress)
        assertEquals("123 Main St", retrieved?.billingAddress?.street)
        assertEquals("Springfield", retrieved?.billingAddress?.city)

        // Cleanup
        customerRepository.deleteBySecret(customerSecret)
        addressRepository.deleteBySecret(billingSecret)
    }

    @Test
    fun `should cascade delete with @PartOf annotation`() {
        // Given
        val billingSecret = generateSecret()
        val billingAddress = TestAddress().withSecret(billingSecret).apply {
            street = "456 Oak Ave"
            city = "Portland"
            zipCode = "97201"
        }
        addressRepository.save(billingAddress)

        val shippingSecret = generateSecret()
        val shippingAddress = TestAddress().withSecret(shippingSecret).apply {
            street = "789 Pine Rd"
            city = "Seattle"
            zipCode = "98101"
        }
        addressRepository.save(shippingAddress)

        // Retrieve fresh instances after saving
        val freshBillingAddress = addressRepository.findBySecretOrNull(billingSecret)!!
        val freshShippingAddress = addressRepository.findBySecretOrNull(shippingSecret)!!

        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Jane Smith"
            this.billingAddress = freshBillingAddress
            this.shippingAddress = freshShippingAddress
        }
        customerRepository.save(customer)

        // Verify addresses exist
        assertTrue(addressRepository.existsBySecret(billingSecret))
        assertTrue(addressRepository.existsBySecret(shippingSecret))

        // When - delete customer (should cascade to addresses)
        customerRepository.deleteBySecret(customerSecret)

        // Then - addresses should also be deleted due to @PartOf
        assertFalse(addressRepository.existsBySecret(billingSecret))
        assertFalse(addressRepository.existsBySecret(shippingSecret))
    }

    @Test
    fun `should NOT cascade delete without @PartOf annotation`() {
        // Given
        val addressSecret = generateSecret()
        val headquarters = TestAddress().withSecret(addressSecret).apply {
            street = "999 Corporate Blvd"
            city = "New York"
            zipCode = "10001"
        }
        addressRepository.save(headquarters)

        val companySecret = generateSecret()
        val company = TestCompany().withSecret(companySecret).apply {
            companyName = "Acme Corp"
            this.headquarters = headquarters
        }
        companyRepository.save(company)

        // Verify address exists
        assertTrue(addressRepository.existsBySecret(addressSecret))

        // When - delete company (should NOT cascade to headquarters)
        companyRepository.deleteBySecret(companySecret)

        // Then - headquarters should still exist (no @PartOf)
        assertTrue(
            addressRepository.existsBySecret(addressSecret),
            "Address should still exist because headquarters field is NOT annotated with @PartOf"
        )

        // Cleanup
        addressRepository.deleteBySecret(addressSecret)
    }

    @Test
    fun `should update nested entity fields`() {
        // Given
        val addressSecret = generateSecret()
        val address = TestAddress().withSecret(addressSecret).apply {
            street = "Original Street"
            city = "Original City"
            zipCode = "00000"
        }
        addressRepository.save(address)

        // Retrieve fresh instance after saving
        val freshAddress = addressRepository.findBySecretOrNull(addressSecret)!!

        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Test Customer"
            billingAddress = freshAddress
        }
        customerRepository.save(customer)

        // When - modify nested entity
        val retrieved = customerRepository.findBySecretOrNull(customerSecret)
        retrieved?.billingAddress?.street = "Updated Street"
        retrieved?.billingAddress?.city = "Updated City"

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        addressRepository.flushThenClear()
        customerRepository.flushThenClear()

        // Then
        val afterUpdate = customerRepository.findBySecretOrNull(customerSecret)
        assertEquals("Updated Street", afterUpdate?.billingAddress?.street)
        assertEquals("Updated City", afterUpdate?.billingAddress?.city)
        assertEquals("00000", afterUpdate?.billingAddress?.zipCode) // Unchanged

        // Cleanup
        customerRepository.deleteBySecret(customerSecret)
        addressRepository.deleteBySecret(addressSecret)
    }

    @Test
    fun `should handle null nested entities`() {
        // Given
        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Customer Without Address"
            billingAddress = null
            shippingAddress = null
        }

        // When
        customerRepository.save(customer)
        val retrieved = customerRepository.findBySecretOrNull(customerSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("Customer Without Address", retrieved?.name)
        assertNull(retrieved?.billingAddress)
        assertNull(retrieved?.shippingAddress)

        // Cleanup
        customerRepository.deleteBySecret(customerSecret)
    }
}