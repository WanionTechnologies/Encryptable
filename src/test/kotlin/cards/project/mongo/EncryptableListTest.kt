package cards.project.mongo

import cards.project.mongo.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for EncryptableList functionality
 * Tests list operations, lazy loading, cascade deletes, and synchronization
 */
class EncryptableListTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var itemRepository: TestItemRepository

    @Autowired
    private lateinit var containerRepository: TestContainerRepository

    @Autowired
    private lateinit var sharedContainerRepository: TestSharedContainerRepository

    @Test
    fun `should save and retrieve list of items`() {
        // Given
        val item1Secret = generateSecret()
        val item1 = TestItem().withSecret(item1Secret).apply {
            name = "Item 1"
            value = 100
        }
        itemRepository.save(item1)

        val item2Secret = generateSecret()
        val item2 = TestItem().withSecret(item2Secret).apply {
            name = "Item 2"
            value = 200
        }
        itemRepository.save(item2)

        // Retrieve fresh instances after saving - this initializes encryption context
        val freshItem1 = itemRepository.findBySecretOrNull(item1Secret)!!
        val freshItem2 = itemRepository.findBySecretOrNull(item2Secret)!!

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Test Container"
            items = mutableListOf(freshItem1, freshItem2)
        }

        // When
        containerRepository.save(container)
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)

        // Then
        assertNotNull(retrieved)
        assertEquals("Test Container", retrieved?.title)
        assertEquals(2, retrieved?.items?.size)
        assertEquals("Item 1", retrieved?.items?.get(0)?.name)
        assertEquals("Item 2", retrieved?.items?.get(1)?.name)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
        itemRepository.deleteBySecret(item1Secret)
        itemRepository.deleteBySecret(item2Secret)
    }

    @Test
    fun `should add items to list automatically`() {
        // Given
        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Dynamic List"
        }
        containerRepository.save(container)

        // When - add item to list
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)

        val newItemSecret = generateSecret()
        val newItem = TestItem().withSecret(newItemSecret).apply {
            name = "New Item"
            value = 999
        }
        itemRepository.save(newItem)

        // Retrieve fresh item instance
        val freshItem = itemRepository.findBySecretOrNull(newItemSecret)!!

        retrieved?.items?.add(freshItem)

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        containerRepository.flushThenClear()

        // Then
        val afterAdd = containerRepository.findBySecretOrNull(containerSecret)
        assertEquals(1, afterAdd?.items?.size)
        assertEquals("New Item", afterAdd?.items?.get(0)?.name)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
        itemRepository.deleteBySecret(newItemSecret)
    }

    @Test
    fun `should remove items from list with cascade delete when @PartOf`() {
        // Given
        val item1Secret = generateSecret()
        val item1 = TestItem().withSecret(item1Secret).apply {
            name = "Item to Remove"
            value = 111
        }
        itemRepository.save(item1)

        val item2Secret = generateSecret()
        val item2 = TestItem().withSecret(item2Secret).apply {
            name = "Item to Keep"
            value = 222
        }
        itemRepository.save(item2)

        // Retrieve fresh instances
        val freshItem1 = itemRepository.findBySecretOrNull(item1Secret)!!
        val freshItem2 = itemRepository.findBySecretOrNull(item2Secret)!!

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Remove Test"
            items = mutableListOf(freshItem1, freshItem2)
        }
        containerRepository.save(container)

        // When - remove item from list
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)
        retrieved?.items?.removeAt(0) // Remove first item

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        containerRepository.flushThenClear()

        // Then - item should be deleted from DB (cascade due to @PartOf)
        assertFalse(itemRepository.existsBySecret(item1Secret), "Item should be deleted due to @PartOf")
        assertTrue(itemRepository.existsBySecret(item2Secret), "Item should still exist")

        val afterRemove = containerRepository.findBySecretOrNull(containerSecret)
        assertEquals(1, afterRemove?.items?.size)
        assertEquals("Item to Keep", afterRemove?.items?.get(0)?.name)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
        itemRepository.deleteBySecret(item2Secret)
    }

    @Test
    fun `should NOT cascade delete items without @PartOf`() {
        // Given
        val itemSecret = generateSecret()
        val item = TestItem().withSecret(itemSecret).apply {
            name = "Shared Item"
            value = 333
        }
        itemRepository.save(item)

        // Retrieve fresh instance
        val freshItem = itemRepository.findBySecretOrNull(itemSecret)!!

        val containerSecret = generateSecret()
        val container = TestSharedContainer().withSecret(containerSecret).apply {
            name = "Shared Container"
            sharedItems = mutableListOf(freshItem)
        }
        sharedContainerRepository.save(container)

        // When - remove item from list
        val retrieved = sharedContainerRepository.findBySecretOrNull(containerSecret)
        retrieved?.sharedItems?.removeAt(0)

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        sharedContainerRepository.flushThenClear()
        itemRepository.flushThenClear()

        // Then - item should still exist (no @PartOf)
        assertTrue(
            itemRepository.existsBySecret(itemSecret),
            "Item should still exist because list is NOT annotated with @PartOf"
        )

        // Cleanup
        sharedContainerRepository.deleteBySecret(containerSecret)
        itemRepository.deleteBySecret(itemSecret)
    }

    @Test
    fun `should clear all items from list`() {
        // Given
        val secrets = (1..3).map { generateSecret() }
        val items = secrets.mapIndexed { index, secret ->
            TestItem().withSecret(secret).apply {
                name = "Item $index"
                value = index * 100
            }
        }
        items.forEach { itemRepository.save(it) }

        // Retrieve fresh instances
        val freshItems = secrets.map { itemRepository.findBySecretOrNull(it)!! }.toMutableList()

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Clear Test"
            this.items = freshItems
        }
        containerRepository.save(container)

        // When - clear the list
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)
        retrieved?.items?.clear()

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        containerRepository.flushThenClear()
        itemRepository.flushThenClear()

        // Then - all items should be deleted (cascade due to @PartOf)
        secrets.forEach { secret ->
            assertFalse(itemRepository.existsBySecret(secret), "Items should be deleted on clear()")
        }

        val afterClear = containerRepository.findBySecretOrNull(containerSecret)
        assertEquals(0, afterClear?.items?.size)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
    }

    @Test
    fun `should lazy load list items on iteration`() {
        // Given
        val secrets = (1..5).map { generateSecret() }
        val items = secrets.mapIndexed { index, secret ->
            TestItem().withSecret(secret).apply {
                name = "Lazy Item $index"
                value = index
            }
        }
        items.forEach { itemRepository.save(it) }

        // Retrieve fresh instances
        val freshItems = secrets.map { itemRepository.findBySecretOrNull(it)!! }.toMutableList()

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Lazy Load Test"
            this.items = freshItems
        }
        containerRepository.save(container)

        // When - retrieve and iterate
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)

        // Then - items should load during iteration
        var count = 0
        retrieved?.items?.forEach { item ->
            assertNotNull(item.name)
            assertTrue(item.name?.startsWith("Lazy Item") == true)
            count++
        }
        assertEquals(5, count)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
        secrets.forEach { itemRepository.deleteBySecret(it) }
    }

    @Test
    fun `should modify items within list`() {
        // Given
        val itemSecret = generateSecret()
        val item = TestItem().withSecret(itemSecret).apply {
            name = "Original Name"
            value = 100
        }
        itemRepository.save(item)

        // Retrieve fresh instance
        val freshItem = itemRepository.findBySecretOrNull(itemSecret)!!

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Modify Test"
            items = mutableListOf(freshItem)
        }
        containerRepository.save(container)

        // When - modify item in list
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)
        retrieved?.items?.get(0)?.name = "Modified Name"
        retrieved?.items?.get(0)?.value = 999

        // simulate the end of the request.
        // this shouldn't be done manually in real code.
        itemRepository.flushThenClear()
        containerRepository.flushThenClear()

        // Then - changes should persist
        val afterModify = containerRepository.findBySecretOrNull(containerSecret)
        assertEquals("Modified Name", afterModify?.items?.get(0)?.name)
        assertEquals(999, afterModify?.items?.get(0)?.value)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
        itemRepository.deleteBySecret(itemSecret)
    }

    @Test
    fun `should get list size correctly`() {
        // Given
        val secrets = (1..10).map { generateSecret() }
        val items = secrets.mapIndexed { index, secret ->
            TestItem().withSecret(secret).apply {
                name = "Item $index"
                value = index
            }
        }
        items.forEach { itemRepository.save(it) }

        // Retrieve fresh instances
        val freshItems = secrets.map { itemRepository.findBySecretOrNull(it)!! }.toMutableList()

        val containerSecret = generateSecret()
        val container = TestContainer().withSecret(containerSecret).apply {
            title = "Size Test"
            this.items = freshItems
        }
        containerRepository.save(container)

        // When
        val retrieved = containerRepository.findBySecretOrNull(containerSecret)

        // Then
        assertEquals(10, retrieved?.items?.size)
        assertFalse(retrieved?.items?.isEmpty() == true)

        // Cleanup
        containerRepository.deleteBySecret(containerSecret)
        secrets.forEach { itemRepository.deleteBySecret(it) }
    }
}