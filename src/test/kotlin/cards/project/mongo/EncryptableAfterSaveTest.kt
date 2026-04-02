package cards.project.mongo

import cards.project.mongo.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for protecting mirror fields from modification after an entity has been saved.
 *
 * ## Why This Matters
 *
 * After an entity is saved, it is not tracked for changes by the Encryptable framework.
 * If you were allowed to set a nested entity field to null after save, the following would happen:
 *
 * 1. The aspect detects the null assignment and deletes the external reference
 *    (the nested entity, list items, or storage files)
 * 2. The entity itself is NOT updated (not tracked, no dirty flag)
 * 3. The entity is left in an **inconsistent state**:
 *    - The mirror field still contains the old reference data
 *    - But the objects/files they point to have been deleted
 * 4. On next load, the entity will fail to reconstruct because referenced data is gone
 *
 * This would cause **data corruption** and cascading failures.
 *
 * ## Solution
 *
 * Mirror fields are protected from modification after save by throwing IllegalStateException.
 * This prevents accidental data loss and maintains entity consistency.
 *
 * Mirror fields include:
 * - `encryptableFieldMap` - stores encrypted single nested entity secrets
 * - `encryptableListFieldMap` - stores encrypted lists of nested entity secrets
 * - `storageFieldIdMap` - stores GridFS/storage references
 */
class EncryptableAfterSaveTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var containerRepository: TestContainerRepository

    @Autowired
    private lateinit var addressRepository: TestAddressRepository

    @Autowired
    private lateinit var customerRepository: TestCustomerRepository

    @Autowired
    private lateinit var slicedRepository: TestSlicedEntityRepository

    @Autowired
    private lateinit var item1Repository: TestItemRepository

    @Autowired
    private lateinit var item2Repository: TestItemRepository

    /**
     * Test that setting a nested entity field to null after save throws IllegalStateException.
     * Setting to null means deleting the reference, which is not allowed for saved mirror fields.
     */
    @Test
    fun `should throw when setting nested entity field to null after save`() {
        // Given - create and save a customer with a nested address
        val addressSecret = generateSecret()
        val address = TestAddress().withSecret(addressSecret).apply {
            street = "123 Main St"
            city = "Springfield"
            zipCode = "12345"
        }
        addressRepository.save(address)

        val freshAddress = addressRepository.findBySecretOrNull(addressSecret)!!

        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "John Doe"
            billingAddress = freshAddress
        }
        customerRepository.save(customer)

        // When & Then - try to set the nested entity field to null (delete operation)
        assertThrows(
            IllegalStateException::class.java,
            { customer.billingAddress = null },
            "Should throw when setting nested entity field to null after save"
        )

        assertNotNull(addressRepository.findBySecretOrNull(addressSecret), "Address should still exist after failed deletion attempt")

        // Cleanup
        customerRepository.deleteBySecret(customerSecret)
        addressRepository.deleteBySecret(addressSecret)
    }

    /**
     * Test that clearing a list of nested entities after save throws IllegalStateException.
     * Clearing the list means deleting all references, which is not allowed for saved mirror fields.
     */
    @Test
    fun `should throw when clearing list of nested entities after save`() {
        // Given - create and save a container with a list of items
        val itemSecret1 = generateSecret()
        val item1 = TestItem().withSecret(itemSecret1).apply {
            name = "Item 1"
            value = 100
        }

        val itemSecret2 = generateSecret()
        val item2 = TestItem().withSecret(itemSecret2).apply {
            name = "Item 2"
            value = 200
        }

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "My Container"
            items.add(item1)
            items.add(item2)
        }
        containerRepository.save(container)

        // When & Then - try to clear the list (delete all references)
        assertThrows(
            IllegalStateException::class.java,
            { container.items.clear() },
            "Should throw when clearing list of nested entities after save"
        )

        assertNotNull(item1Repository.findBySecretOrNull(itemSecret1), "Item 1 should still exist after failed deletion attempt")
        assertNotNull(item2Repository.findBySecretOrNull(itemSecret2), "Item 2 should still exist after failed deletion attempt")

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
    }

    /**
     * Test that setting a sliced ByteArray field to null after save throws IllegalStateException.
     * Setting to null means deleting the storage references, which is not allowed for saved mirror fields.
     */
    @Test
    fun `should throw when setting sliced field to null after save`() {
        // Given - create and save an entity with a sliced ByteArray field
        val secret = generateSecret()
        val slicedContent = createSampleBytes(5) // 5KB
        val entity = TestSlicedEntity().withSecret(secret).apply {
            this.name = "Sliced Entity"
            this.slicedContent = slicedContent
        }
        slicedRepository.save(entity)

        // When & Then - try to set the sliced field to null (delete all storage references)
        assertThrows(
            IllegalStateException::class.java,
            { entity.slicedContent = null },
            "Should throw when setting sliced field to null after save"
        )

        assertNotNull(slicedRepository.findBySecretOrNull(secret), "Sliced entity should still exist with content after failed deletion attempt")

        // Cleanup
        slicedRepository.deleteBySecret(secret)
    }

    /**
     * Test that modifying a nested entity's reference after save throws IllegalStateException.
     */
    @Test
    fun `should throw when trying to remove nested entity reference after save`() {
        // Given
        val addressSecret = generateSecret()
        val address = TestAddress().withSecret(addressSecret).apply {
            street = "456 Oak Ave"
            city = "Portland"
            zipCode = "97201"
        }
        addressRepository.save(address)

        val freshAddress = addressRepository.findBySecretOrNull(addressSecret)!!

        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Jane Smith"
            billingAddress = freshAddress
        }
        customerRepository.save(customer)

        // Simulate end of request
        customerRepository.flushThenClear()

        assertThrows(
            IllegalStateException::class.java,
            { customer.billingAddress = null },
            "Should throw when trying to remove nested entity reference after save"
        )

        assertNotNull(addressRepository.findBySecretOrNull(addressSecret), "Address should exist after trying to clear it from the customer.")

        // Cleanup
        customerRepository.deleteBySecret(customerSecret)
        addressRepository.deleteBySecret(addressSecret)
    }

    /**
     * Test that the encryptableFieldTypeMap is not directly modifiable after save.
     * Attempting to delete by setting the field to null should throw.
     */
    @Test
    fun `should throw when trying to set second nested entity field to null after save`() {
        // Given
        val addressSecret1 = generateSecret()
        val address1 = TestAddress().withSecret(addressSecret1).apply {
            street = "789 Pine Rd"
            city = "Seattle"
            zipCode = "98101"
        }
        addressRepository.save(address1)

        val addressSecret2 = generateSecret()
        val address2 = TestAddress().withSecret(addressSecret2).apply {
            street = "456 Oak Ave"
            city = "Portland"
            zipCode = "97201"
        }
        addressRepository.save(address2)

        val freshAddress1 = addressRepository.findBySecretOrNull(addressSecret1)!!
        val freshAddress2 = addressRepository.findBySecretOrNull(addressSecret2)!!

        val customerSecret = generateSecret()
        val customer = TestCustomer().withSecret(customerSecret).apply {
            name = "Test Customer"
            billingAddress = freshAddress1
            shippingAddress = freshAddress2
        }
        customerRepository.save(customer)

        // When & Then - try to delete the shipping address by setting to null
        assertThrows(
            IllegalStateException::class.java,
            { customer.shippingAddress = null },
            "Should throw when trying to set second nested entity field to null after save"
        )

        assertNotNull(addressRepository.findBySecretOrNull(addressSecret1))
        assertNotNull(addressRepository.findBySecretOrNull(addressSecret2))

        // Cleanup
        customerRepository.deleteBySecret(customerSecret)
        addressRepository.deleteBySecret(addressSecret1)
        addressRepository.deleteBySecret(addressSecret2)
    }

    /**
     * Test that adding items to a list after retrieval requires explicit save to persist.
     */
    @Test
    fun `should throw when trying to add item to list after save`() {
        // Given
        val item1Secret = generateSecret()
        val item1 = TestItem().withSecret(item1Secret).apply {
            name = "Original Item"
            value = 100
        }

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "My Container"
            items.add(item1)
        }
        containerRepository.save(container)

        // Simulate end of request
        containerRepository.flushThenClear()

        // When - try to add item after save
        val newItemSecret = generateSecret()
        val newItem = TestItem().withSecret(newItemSecret).apply {
            name = "New Item"
            value = 200
        }
        
        assertThrows(
            IllegalStateException::class.java,
            { container.items.add(newItem) },
            "Should throw when trying to add item to list after save without explicit save"
        )

        assertEquals(1, container.items.size, "List should still contain only the original item after failed addition attempt")

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
    }
}

