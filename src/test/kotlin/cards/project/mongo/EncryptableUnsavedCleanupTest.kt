package cards.project.mongo

import cards.project.mongo.entity.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for automatic cleanup of unsaved entities at request end.
 *
 * When an entity is created but not saved by the end of the request lifecycle,
 * it should be automatically cascade-deleted along with:
 * - Nested entities annotated with @PartOf
 * - List items annotated with @PartOf
 * - Associated GridFS files
 *
 * This prevents orphaned resources from accumulating in the database.
 */
class EncryptableUnsavedCleanupTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var addressRepository: TestAddressRepository

    @Autowired
    private lateinit var customerRepository: TestCustomerRepository

    @Autowired
    private lateinit var containerRepository: TestContainerRepository

    @Autowired
    private lateinit var itemRepository: TestItemRepository

    @Autowired
    private lateinit var companyRepository: TestCompanyRepository

    @Test
    fun `should cleanup unsaved entity with no nested entities`() {
        // Given - create an address but don't save it
        val addressSecret = generateSecret()
        val address = TestAddress().withSecret(addressSecret).apply {
            street = "123 Unsaved St"
            city = "Nowhere"
            zipCode = "00000"
        }

        // Verify the entity is marked as new
        assertTrue(address.isNew(), "Address should be new (unsaved)")

        // When
        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        addressRepository.flushThenClear()

        // Then - the unsaved entity should be cleaned up
        assertFalse(
            addressRepository.existsBySecret(addressSecret),
            "Unsaved address should be cleaned up at request end"
        )
    }

    @Test
    fun `should NOT cleanup entity that was saved`() {
        // Given - create and save an address
        val addressSecret = generateSecret()
        val address = TestAddress().withSecret(addressSecret).apply {
            street = "456 Saved Ave"
            city = "Somewhere"
            zipCode = "12345"
        }
        addressRepository.save(address)

        // Verify the entity is no longer new
        assertFalse(address.isNew(), "Address should not be new after save")

        // When
        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        addressRepository.flushThenClear()

        // Then - the saved entity should still exist
        assertTrue(
            addressRepository.existsBySecret(addressSecret),
            "Saved address should NOT be cleaned up at request end"
        )

        // Cleanup
        addressRepository.deleteBySecret(addressSecret)
    }

    @Test
    fun `should cleanup unsaved entity with nested @PartOf entities`() {
        // Given - create nested entities but don't save the parent
        val billingSecret = generateSecret()
        val billingAddress = TestAddress().withSecret(billingSecret).apply {
            street = "111 Billing St"
            city = "BillCity"
            zipCode = "11111"
        }
        // Save the nested entity first
        addressRepository.save(billingAddress)

        val shippingSecret = generateSecret()
        val shippingAddress = TestAddress().withSecret(shippingSecret).apply {
            street = "222 Shipping Ave"
            city = "ShipCity"
            zipCode = "22222"
        }
        // Save the nested entity first
        addressRepository.save(shippingAddress)

        // Retrieve fresh instances
        val freshBilling = addressRepository.findBySecretOrNull(billingSecret)!!
        val freshShipping = addressRepository.findBySecretOrNull(shippingSecret)!!

        // Create parent customer but DON'T save it
        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Unsaved Customer"
            this.billingAddress = freshBilling
            this.shippingAddress = freshShipping
        }

        // Verify addresses exist before cleanup
        assertTrue(addressRepository.existsBySecret(billingSecret))
        assertTrue(addressRepository.existsBySecret(shippingSecret))
        assertTrue(customer.isNew(), "Customer should be new (unsaved)")

        // When

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        customerRepository.flushThenClear()

        // Then - customer should be cleaned up, and @PartOf addresses should be cascaded deleted
        assertFalse(
            customerRepository.existsBySecret(customerSecret),
            "Unsaved customer should be cleaned up"
        )
        assertFalse(
            addressRepository.existsBySecret(billingSecret),
            "Billing address should be cascade deleted (marked with @PartOf)"
        )
        assertFalse(
            addressRepository.existsBySecret(shippingSecret),
            "Shipping address should be cascade deleted (marked with @PartOf)"
        )
    }

    @Test
    fun `should cleanup unsaved entity with list of @PartOf entities`() {
        // Given - create items for a container
        val item1Secret = generateSecret()
        val item1 = TestItem().withSecret(item1Secret).apply {
            name = "Unsaved Item 1"
            value = 10
        }
        itemRepository.save(item1)

        val item2Secret = generateSecret()
        val item2 = TestItem().withSecret(item2Secret).apply {
            name = "Unsaved Item 2"
            value = 20
        }
        itemRepository.save(item2)

        // Retrieve fresh instances
        val freshItem1 = itemRepository.findBySecretOrNull(item1Secret)!!
        val freshItem2 = itemRepository.findBySecretOrNull(item2Secret)!!

        // Create container with items but DON'T save it
        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Unsaved Container"
            items.add(freshItem1)
            items.add(freshItem2)
        }

        // Verify items exist before cleanup
        assertTrue(itemRepository.existsBySecret(item1Secret))
        assertTrue(itemRepository.existsBySecret(item2Secret))
        assertTrue(container.isNew(), "Container should be new (unsaved)")

        // When

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        containerRepository.flushThenClear()

        // Then - container and items should be cleaned up
        assertFalse(
            containerRepository.existsBySecret(containerSecret),
            "Unsaved container should be cleaned up"
        )
        assertFalse(
            itemRepository.existsBySecret(item1Secret),
            "Item 1 should be cascade deleted (list marked with @PartOf)"
        )
        assertFalse(
            itemRepository.existsBySecret(item2Secret),
            "Item 2 should be cascade deleted (list marked with @PartOf)"
        )
    }

    @Test
    fun `should NOT cleanup nested entities without @PartOf`() {
        // Given - create a company with headquarters (NOT marked with @PartOf)
        val addressSecret = generateSecret()
        val headquarters = TestAddress().withSecret(addressSecret).apply {
            street = "999 Corporate Blvd"
            city = "MetroCity"
            zipCode = "99999"
        }
        addressRepository.save(headquarters)

        // Create company but DON'T save it
        val companySecret = generateSecret()
        val company = TestCompany().withSecret(companySecret).apply {
            companyName = "Unsaved Corp"
            this.headquarters = headquarters
        }

        // Verify address exists
        assertTrue(addressRepository.existsBySecret(addressSecret))
        assertTrue(company.isNew(), "Company should be new (unsaved)")

        // When

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        companyRepository.flushThenClear()

        // Then - company should be cleaned up, but headquarters should remain (no @PartOf)
        assertFalse(
            companyRepository.existsBySecret(companySecret),
            "Unsaved company should be cleaned up"
        )
        assertTrue(
            addressRepository.existsBySecret(addressSecret),
            "Headquarters should NOT be cascade deleted (not marked with @PartOf)"
        )

        // Cleanup
        addressRepository.deleteBySecret(addressSecret)
    }

    @Test
    fun `should cleanup multiple unsaved entities independently`() {
        // Given - create multiple unsaved entities
        val address1Secret = generateSecret()
        val address1 = TestAddress().withSecret(address1Secret).apply {
            street = "100 First St"
            city = "City1"
            zipCode = "10000"
        }

        val address2Secret = generateSecret()
        val address2 = TestAddress().withSecret(address2Secret).apply {
            street = "200 Second St"
            city = "City2"
            zipCode = "20000"
        }

        val address3Secret = generateSecret()
        val address3 = TestAddress().withSecret(address3Secret).apply {
            street = "300 Third St"
            city = "City3"
            zipCode = "30000"
        }
        // Save only the third one
        addressRepository.save(address3)

        // Verify states
        assertTrue(address1.isNew())
        assertTrue(address2.isNew())
        assertFalse(address3.isNew())

        // When

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        addressRepository.flushThenClear()

        // Then - only unsaved entities should be cleaned up
        assertFalse(
            addressRepository.existsBySecret(address1Secret),
            "Unsaved address1 should be cleaned up"
        )
        assertFalse(
            addressRepository.existsBySecret(address2Secret),
            "Unsaved address2 should be cleaned up"
        )
        assertTrue(
            addressRepository.existsBySecret(address3Secret),
            "Saved address3 should still exist"
        )

        // Cleanup
        addressRepository.deleteBySecret(address3Secret)
    }

    @Test
    fun `should handle complex nested structure cleanup`() {
        // Given - create a complex structure with multiple levels
        // Level 3: Items
        val item1Secret = generateSecret()
        val item1 = TestItem().withSecret(item1Secret).apply {
            name = "Deep Item 1"
            value = 5
        }
        itemRepository.save(item1)

        val item2Secret = generateSecret()
        val item2 = TestItem().withSecret(item2Secret).apply {
            name = "Deep Item 2"
            value = 15
        }
        itemRepository.save(item2)

        val freshItem1 = itemRepository.findBySecretOrNull(item1Secret)!!
        val freshItem2 = itemRepository.findBySecretOrNull(item2Secret)!!

        // Level 2: Container (not saved)
        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Deep Container"
            items.add(freshItem1)
            items.add(freshItem2)
        }

        // Level 1: Addresses (saved)
        val addressSecret = generateSecret()
        val address = TestAddress().withSecret(addressSecret).apply {
            street = "Deep Address St"
            city = "DeepCity"
            zipCode = "99999"
        }
        addressRepository.save(address)

        val freshAddress = addressRepository.findBySecretOrNull(addressSecret)!!

        // Level 0: Customer (not saved) - root of the hierarchy
        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Deep Customer"
            this.billingAddress = freshAddress
        }

        // Verify all entities exist
        assertTrue(itemRepository.existsBySecret(item1Secret))
        assertTrue(itemRepository.existsBySecret(item2Secret))
        assertTrue(addressRepository.existsBySecret(addressSecret))
        assertTrue(customer.isNew())
        assertTrue(container.isNew())

        // When - simulate end of request
        customerRepository.flushThenClear()
        containerRepository.flushThenClear()

        // Then - verify cleanup behavior
        // Customer cleanup should cascade to billing address
        assertFalse(
            customerRepository.existsBySecret(customerSecret),
            "Unsaved customer should be cleaned up"
        )
        assertFalse(
            addressRepository.existsBySecret(addressSecret),
            "Address should be cascade deleted (marked with @PartOf)"
        )

        // Container cleanup should cascade to items
        assertFalse(
            containerRepository.existsBySecret(containerSecret),
            "Unsaved container should be cleaned up"
        )
        assertFalse(
            itemRepository.existsBySecret(item1Secret),
            "Item 1 should be cascade deleted"
        )
        assertFalse(
            itemRepository.existsBySecret(item2Secret),
            "Item 2 should be cascade deleted"
        )
    }
}