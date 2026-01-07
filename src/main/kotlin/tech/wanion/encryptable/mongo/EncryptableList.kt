package tech.wanion.encryptable.mongo

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import tech.wanion.encryptable.EncryptableContext
import tech.wanion.encryptable.util.Limited.parallelForEach
import tech.wanion.encryptable.util.extensions.getField
import tech.wanion.encryptable.util.extensions.isListOf
import tech.wanion.encryptable.util.extensions.typeParameter

/**
 * # EncryptableList
 *
 * A thread-safe proxy mutable list that lazy loads elements from a source list of Strings on access and persists changes to MongoDB.
 *
 * ---
 *
 * ## Features
 * - Implements [MutableList] for type `T : Encryptable<T>`.
 * - Lazy loads elements from the provided `secretList` only when accessed, caching them for future use.
 * - Mutation operations (`add`, `addAll`, `removeAt`, `clear`, etc.) are atomic: both the cache and the underlying secret list are only updated if the database operation succeeds.
 *   If the database operation fails, no in-memory changes are made.
 * - All public methods are thread-safe using a private [ReentrantLock].
 * - Efficient batch loading for iteration: missing entries are loaded in bulk when iterating.
 * - Parent entity is kept up-to-date via `encryptable.updateSecretInfo()` after mutations.
 * - **Part-of relationship support:**
 *   - If the list field is annotated with `@PartOf`, elements are considered a strong composition ("part-of") of the parent entity.
 *   - When an element is removed from a list annotated with `@PartOf`, it is also deleted from the database (cascade delete).
 *   - If the list is not annotated, elements are treated as simple references: removing an element only removes the reference, not the entity itself.
 *
 * ---
 *
 * ## Limitations
 * - The cache (backingList) is always kept in sync with the underlying secretList.
 *   When elements are removed, their cached values are removed, so there is no leftover cached data for removed elements.
 * - Mutating the list (adding/removing elements) may affect the mapping between indices and cached entries.
 * - **Not implemented methods:** The following methods will throw [NotImplementedError] if called:
 *   - `subList`
 *   - `contains`
 *   - `containsAll`
 *   - `indexOf`
 *   - `lastIndexOf`
 *   - `remove`
 *   - `removeAll`
 *   - `retainAll`
 *   - `set`
 *   - `listIterator(index: Int)`
 *
 * ---
 *
 * ## Atomicity & Error Handling
 * - **Atomicity Guarantee:** All mutation operations are atomic: either both the database and in-memory changes succeed, or neither does. If a database operation fails, the in-memory list remains unchanged.
 * - **Note:** If you require batch atomicity for addAll/clear, ensure your MongoDB setup supports transactions.
 *
 * ---
 *
 * ## Parameters
 * - `fieldName`: The name of the field in the parent entity that holds the list of secrets.
 * - `encryptable`: The parent entity containing the field.
 * - `partOf`: Boolean indicating if the field is annotated with `@PartOf` (composition/cascade delete behavior).
 *
 * ---
 *
 * ## Example Usage
 * ```kotlin
 * val myList = EncryptableList<MyEntity>("myField", parentEntity)
 * myList.add(newEntity) // Automatically saves to DB if DB succeeds
 * val item = myList[0] // Lazy loads from DB if needed
 * myList.removeAt(0)   // Removes from DB and list if DB succeeds (cascade delete if @PartOf)
 * ```
 *
 * ---
 *
 * ## See Also
 * - [MutableList]
 * - [ReentrantLock]
 * - [Encryptable]
 * - [EncryptableMongoRepository]
 * - [PartOf]
 **/
@Suppress("UNCHECKED_CAST")
class EncryptableList<T: Encryptable<T>>(
    fieldName: String,
    val encryptable: Encryptable<*>
) : MutableList<T> {
    /** Lock for thread-safety. */
    private val lock = ReentrantLock()
    /** The underlying list of secrets backing this EncryptableList. */
    private val secretList: MutableList<String>
    /** The class type of the list elements.
     * Used for repository lookups and type validation.
     */
    private val typeClass: Class<T>
    /** Repository for type T entities. */
    private val encryptableMongoRepository: EncryptableMongoRepository<T>
    /** Indicates whether the list is part of a parent entity (annotated with @PartOf). */
    private val partOf: Boolean
    /** Indicates whether the list is part of a cryptographically isolated Entity (id annotated with @HKDFId). */
    private val isolated: Boolean

    init {
        val metadata = encryptable.metadata
        val field = metadata.encryptableListFields[fieldName] ?: throw IllegalArgumentException("Field '$fieldName' not found in ${encryptable::class.java.name}")
        if (!List::class.java.isAssignableFrom(field.type)) throw IllegalArgumentException("Field '$fieldName' is not a List in ${encryptable::class.java.name}")
        // Extract generic type parameter of the Field's List<T>
        val typeParameter = field.typeParameter()
        this.typeClass = (typeParameter as? Class<T>) ?: throw IllegalArgumentException("Generic type of List for field '$fieldName' is not a Encryptable class type")
        val fieldEncryptablesMap = encryptable.getField<MutableMap<String, MutableList<String>>>("encryptableListFieldMap")
        if (!fieldEncryptablesMap.containsKey(fieldName))
            fieldEncryptablesMap[fieldName] = mutableListOf()
        this.secretList = fieldEncryptablesMap[fieldName]!!
        // Get the repository for the typeClass.
        this.encryptableMongoRepository = EncryptableContext.getRepositoryForEncryptableClass(typeClass)
        this.partOf = field.getAnnotation(PartOf::class.java) != null
        this.isolated = metadata.isolated
    }

    /**
     * Secondary constructor to initialize the list with a starting list of elements.
     *
     * @param startingList Initial elements to populate the list.
     */
    constructor(fieldName: String, encryptable: Encryptable<*>, startingList: List<*>) : this(fieldName, encryptable) {
        // Initialize from startingList
        // if startingList is empty, we simply assume that it is of type List<T>
        if (startingList.isEmpty())
            return
        // if starting list isn't empty, we Validate that startingList is of type List<T>
        require(startingList.isListOf(typeClass)) { "startingList is not a List of ${typeClass.name}" }
        addAll(startingList as List<T>)
    }

    /**
     * Internal backing list storing loaded elements or null for unloaded slots.
     * The size is always kept in sync with [secretList].
     */
    private val backingList = mutableListOf<T?>().apply { repeat(secretList.size) { add(null) } }

    /**
     * Returns the current size of the list.
     */
    override val size: Int get() = lock.withLock { secretList.size }

    /**
     * Returns true if the list contains no elements.
     */
    override fun isEmpty(): Boolean = lock.withLock { size == 0 }

    /**
     * Returns the element at the specified [index], loading it from [secretList] if not already cached.
     *
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    override fun get(index: Int): T = lock.withLock {
        require(index in 0 until size) { "Index out of bounds: $index" }
        if (backingList[index] == null)
            backingList[index] = encryptableMongoRepository.findBySecretOrNull(secretList[index], true)
                ?: throw IllegalStateException("No entity found in DB for secret '${secretList[index]}' at index $index")
        return backingList[index] as T
    }

    /**
     * Returns a mutable iterator over the elements in the list.
     * Elements are loaded lazily as iterated.
     */
    override fun iterator(): MutableIterator<T> {
        lock.withLock {
            val missingIndices = backingList.indices.filter { backingList[it] == null }
            if (missingIndices.isNotEmpty()) {
                val secretsToLoad = missingIndices.map { secretList[it] }
                val loaded = encryptableMongoRepository.findBySecrets(secretsToLoad, true)
                val secretToObject = loaded.associateBy { Encryptable.getSecretOf(it) }
                for (i in missingIndices) {
                    backingList[i] = secretToObject[secretList[i]]
                }
            }
        }
        return object : MutableIterator<T> {
            private var current = 0
            override fun hasNext() = lock.withLock { current < size }
            override fun next() = lock.withLock { get(current++) }
            override fun remove(): Unit = lock.withLock {
                val toRemove = get(current - 1)
                try {
                    if (partOf)
                        encryptableMongoRepository.deleteBySecret(Encryptable.getSecretOf(toRemove))
                    secretList.removeAt(--current)
                    backingList.removeAt(current)
                } catch (e: Exception) {
                    // DB operation failed, do not update in-memory list
                    throw e
                }
            }
        }
    }

    /**
     * Returns a mutable list iterator over the elements in the list, starting at the beginning.
     */
    override fun listIterator(): MutableListIterator<T> = listIterator(0)

    /**
     * Returns a live, thread-safe, atomic view of the portion of this list between [fromIndex] (inclusive) and [toIndex] (exclusive).
     *
     * The returned sublist is backed by the parent [EncryptableList], so changes in the sublist are reflected in the parent list and vice versa.
     *
     * - All operations are synchronized and atomic, using the parent list's lock.
     * - Mutations are propagated to the parent list at the correct indices.
     * - The sublist supports only read and add operations. Remove, clear, and set are not supported and will throw [UnsupportedOperationException].
     * - Calling [subList] on the returned sublist is not supported and will throw [UnsupportedOperationException].
     *
     * @param fromIndex the start index (inclusive) of the sublist
     * @param toIndex the end index (exclusive) of the sublist
     * @return a live, thread-safe, atomic sublist view
     * @throws IllegalArgumentException if the indices are invalid
     * @throws UnsupportedOperationException if [subList] is called on the returned sublist
     */
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        lock.withLock {
            if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
                throw IllegalArgumentException("Invalid subList range: $fromIndex to $toIndex (size=$size)")
            }
            return object : MutableList<T> {
                override val size: Int get() = lock.withLock { toIndex - fromIndex }
                override fun isEmpty() = size == 0
                override fun get(index: Int): T = lock.withLock {
                    require(index in 0 until size) { "Index out of bounds: $index (sublist size=$size)" }
                    this@EncryptableList[fromIndex + index]
                }
                override fun add(element: T): Boolean = lock.withLock {
                    this@EncryptableList.add(toIndex, element)
                    true
                }
                override fun add(index: Int, element: T) {
                    lock.withLock {
                        require(index in 0..size) { "Index out of bounds: $index (sublist size=$size)" }
                        this@EncryptableList.add(fromIndex + index, element)
                    }
                }
                override fun addAll(elements: Collection<T>): Boolean = lock.withLock {
                    this@EncryptableList.addAll(toIndex, elements)
                }
                override fun addAll(index: Int, elements: Collection<T>): Boolean = lock.withLock {
                    require(index in 0..size) { "Index out of bounds: $index (sublist size=$size)" }
                    this@EncryptableList.addAll(fromIndex + index, elements)
                }
                override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
                    private var cursor = 0
                    override fun hasNext() = cursor < size
                    override fun next() = get(cursor++)
                    override fun remove() = throw UnsupportedOperationException("Remove is not supported on this subList")
                }
                override fun listIterator(): MutableListIterator<T> = listIterator(0)
                override fun listIterator(index: Int): MutableListIterator<T> = object : MutableListIterator<T> {
                    private var cursor = index
                    override fun hasNext() = cursor < size
                    override fun next() = get(cursor++)
                    override fun hasPrevious() = cursor > 0
                    override fun previous() = get(--cursor)
                    override fun nextIndex() = cursor
                    override fun previousIndex() = cursor - 1
                    override fun add(element: T) = add(cursor++, element)
                    override fun set(element: T) = throw UnsupportedOperationException("Set is not supported on this subList")
                    override fun remove() = throw UnsupportedOperationException("Remove is not supported on this subList")
                }
                override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = throw UnsupportedOperationException("subList of subList is not supported")
                override fun contains(element: T): Boolean = (0 until size).any { get(it) == element }
                override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }
                override fun indexOf(element: T): Int = (0 until size).firstOrNull { get(it) == element } ?: -1
                override fun lastIndexOf(element: T): Int = (size - 1 downTo 0).firstOrNull { get(it) == element } ?: -1
                override fun remove(element: T): Boolean = throw UnsupportedOperationException("Remove is not supported on this subList")
                override fun removeAt(index: Int): T = throw UnsupportedOperationException("Remove is not supported on this subList")
                override fun removeAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("Remove is not supported on this subList")
                override fun retainAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException("Remove is not supported on this subList")
                override fun set(index: Int, element: T): T = throw UnsupportedOperationException("Set is not supported on this subList")
                override fun clear() = throw UnsupportedOperationException("Clear is not supported on this subList")
            }
        }
    }

    /**
     * Adds [element] to the end of the list and saves it to the database.
     *
     * @return true (as per [MutableList] contract).
     */
    override fun add(element: T): Boolean = lock.withLock {
        try {
            val isNew = element.isNew()
            val secret = Encryptable.getSecretOf(element)
            // only save if it is new
            if (isNew)
                encryptableMongoRepository.save(element)
            val secretToSave = if (isolated) secret else element.id?.toString() ?: throw IllegalStateException("Encryptable Element must have an ID after save.")
            val result = backingList.add(element) && secretList.add(secretToSave)
            result
        } catch (_: Exception) {
            // DB operation failed, do not update in-memory list
            return false
        }
    }

    /**
     * Inserts [element] at the specified [index] and saves it to the database.
     *
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    override fun add(index: Int, element: T) = lock.withLock {
        try {
            val isNew = element.isNew()
            val secret = Encryptable.getSecretOf(element)
            // only save if it is new
            if (isNew)
                encryptableMongoRepository.save(element)
            val secretToSave = if (isolated) secret else element.id?.toString() ?: throw IllegalStateException("Encryptable Element must have an ID after save.")
            backingList.add(index, element)
            secretList.add(index, secretToSave)
        } catch (_: Exception) {
            // DB operation failed, do not update in-memory list
        }
    }

    /**
     * Adds all elements in [elements] to the end of the list and saves them to the database.
     *
     * @return true if the list was changed.
     */
    override fun addAll(elements: Collection<T>): Boolean = lock.withLock {
        if (elements.isEmpty())
            return false
        try {
            val newList = elements.filter { it.isNew() }
            val secrets = elements.map { Encryptable.getSecretOf(it) }
            val secretsToSave = secrets.map { secret ->
                if (isolated) secret else {
                    val element = elements.first { Encryptable.getSecretOf(it) == secret }
                    element.id?.toString() ?: throw IllegalStateException("Encryptable Element must have an ID after save.")
                }
            }
            newList.parallelForEach {
                encryptableMongoRepository.save(it)
            }
            backingList.addAll(elements)
            val result = secretList.addAll(secretsToSave)
            result
        } catch (e: Exception) {
            // Log the exception to help with debugging
            println("EncryptableList.addAll() failed with exception: ${e.message}")
            e.printStackTrace()
            // DB operation failed, do not update in-memory list
            return false
        }
    }

    /**
     * Inserts all elements in [elements] at the specified [index] and saves them to the database.
     *
     * @return true if the list was changed.
     */
    override fun addAll(index: Int, elements: Collection<T>): Boolean = lock.withLock {
        try {
            val newList = elements.filter { it.isNew() }
            newList.parallelForEach {
                encryptableMongoRepository.save(it)
            }
            val secrets = elements.map { Encryptable.getSecretOf(it) }
            val secretsToSave = secrets.map { secret ->
                if (isolated) secret else {
                    val element = elements.first { Encryptable.getSecretOf(it) == secret }
                    element.id?.toString() ?: throw IllegalStateException("Encryptable Element must have an ID after save.")
                }
            }
            backingList.addAll(index, elements)
            val result = secretList.addAll(index, secretsToSave)
            result
        } catch (_: Exception) {
            // DB operation failed, do not update in-memory list
            return false
        }
    }

    /**
     * Removes all elements from the list.
     */
    override fun clear() = lock.withLock {
        try {
            if (partOf)
                encryptableMongoRepository.deleteBySecrets(secretList)
            backingList.clear()
            secretList.clear()
        } catch (_: Exception) {
            // DB operation failed, do not update in-memory list
        }
    }

    /**
     * Removes the element at the specified [index].
     *
     * @return the element previously at the specified position.
     * @throws IndexOutOfBoundsException if [index] is out of bounds.
     */
    override fun removeAt(index: Int): T = lock.withLock {
        val old = get(index)
        try {
            if (partOf)
                encryptableMongoRepository.deleteBySecret(Encryptable.getSecretOf(old), true)
            backingList.removeAt(index)
            secretList.removeAt(index)
            return old
        } catch (e: Exception) {
            // DB operation failed, do not update in-memory list
            throw e
        }
    }

    /**
     * Returns a hash code value for the EncryptableList.
     *
     * The hash code is computed based solely on the contents and order of the underlying secretList.
     * This ensures that any change in the list's secrets (add, remove, reorder) will result in a different hash code.
     * The implementation is thread-safe and suitable for change tracking and equality checks.
     *
     * @return the hash code value for this list, based on secretList.
     */
    override fun hashCode(): Int = lock.withLock { secretList.hashCode() }

    /**
     * Intentionally not implemented methods (throws [NotImplementedError]):
     */
    override fun contains(element: T): Boolean = throw NotImplementedError("contains is intentionally not implemented.")
    override fun containsAll(elements: Collection<T>): Boolean = throw NotImplementedError("containsAll is intentionally not implemented.")
    override fun indexOf(element: T): Int = throw NotImplementedError("indexOf is intentionally not implemented.")
    override fun lastIndexOf(element: T): Int = throw NotImplementedError("lastIndexOf is intentionally not implemented.")
    override fun remove(element: T): Boolean = throw NotImplementedError("remove(element: T) is intentionally not implemented.")
    override fun removeAll(elements: Collection<T>): Boolean = throw NotImplementedError("removeAll(elements: Collection<T>) is intentionally not implemented.")
    override fun retainAll(elements: Collection<T>): Boolean = throw NotImplementedError("retainAll(elements: Collection<T>) is intentionally not implemented.")
    override fun set(index: Int, element: T): T = throw NotImplementedError("set(index: Int, element: T) is intentionally not implemented.")
    override fun listIterator(index: Int): MutableListIterator<T> = throw NotImplementedError("listIterator(index: Int) is intentionally not implemented.")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptableList<*>

        return secretList == other.secretList
    }

}