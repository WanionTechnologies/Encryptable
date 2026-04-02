package cards.project.mongo

import cards.project.mongo.entity.TestAddress
import cards.project.mongo.entity.TestAddressRepository
import cards.project.mongo.entity.TestComplexEntity
import cards.project.mongo.entity.TestComplexEntityRepository
import cards.project.mongo.entity.TestContainer
import cards.project.mongo.entity.TestContainerRepository
import cards.project.mongo.entity.TestCustomer
import cards.project.mongo.entity.TestCustomerRepository
import cards.project.mongo.entity.TestIdEncryptEntity
import cards.project.mongo.entity.TestIdEncryptEntityRepository
import cards.project.mongo.entity.TestIdNestedEntity
import cards.project.mongo.entity.TestIdNestedEntityRepository
import cards.project.mongo.entity.TestItem
import cards.project.mongo.entity.TestItemRepository
import cards.project.mongo.entity.TestSimpleRefEntity
import cards.project.mongo.entity.TestSimpleRefEntityRepository
import cards.project.mongo.entity.TestUser
import cards.project.mongo.entity.TestUserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tech.wanion.encryptable.MasterSecretHolder
import tech.wanion.encryptable.CID
import tech.wanion.encryptable.storage.StorageHandler
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.extensions.decode64
import tech.wanion.encryptable.util.extensions.readField
import tech.wanion.encryptable.util.extensions.metadata
import java.security.SecureRandom

/**
 * # Key Correctness Tests
 *
 * These tests close the gap described in MISSED_CALLSITE_BUG_1_0_8.md.
 *
 * ## What round-trip tests cannot catch
 *
 * A standard integration test saves an entity, reloads it, and asserts the value matches.
 * If the wrong key is used consistently for both encrypt and decrypt, the round-trip still passes —
 * the test never observes that the key is wrong.
 *
 * ## What these tests do instead
 *
 * After `save()`, we read the raw encrypted bytes **directly** — without involving the framework's
 * decrypt path at all — and then manually call `AES256.decrypt` with:
 *
 *   1. The **correct** key — must yield the original plaintext.
 *   2. The **wrong** key — must return the encrypted payload unchanged (AES-GCM auth failure).
 *
 * For **inline fields** (below threshold), the field holds ciphertext in memory after save.
 * We read it via reflection.
 *
 * For **storage-backed fields** (above threshold), the in-memory field holds a reference after save,
 * not ciphertext. We read the raw encrypted bytes the framework actually wrote by going through
 * `StorageHandler` directly — bypassing the framework's read/decrypt path entirely.
 *
 * Both paths are fully covered:
 *   - `@HKDFId` inline String — correct key is the entity's own secret.
 *   - `@Id` inline String — correct key is the master secret, NOT the CID.
 *   - `@HKDFId` inline List<String> — same rule, verified per element.
 *   - `@Id` inline List<String> — same rule, verified per element.
 *   - `@HKDFId` inline ByteArray — correct key is the entity's own secret.
 *   - `@Id` inline ByteArray — correct key is the master secret, NOT the CID.
 *   - `@HKDFId` storage-backed ByteArray — same rule, verified against what was actually stored.
 *   - `@Id` storage-backed ByteArray — same rule, verified against what was actually stored.
 *
 * If the framework ever regresses to encrypting with the wrong key, assertion (1) will fail because
 * decrypting with the correct key will produce garbage instead of the original plaintext.
 */
class EncryptableKeyCorrectnessTest : BaseEncryptableTest() {

    @Autowired
    private lateinit var userRepository: TestUserRepository

    @Autowired
    private lateinit var idEncryptRepository: TestIdEncryptEntityRepository

    @Autowired
    private lateinit var complexRepository: TestComplexEntityRepository

    @Autowired
    private lateinit var containerRepository: TestContainerRepository

    @Autowired
    private lateinit var itemRepository: TestItemRepository

    @Autowired
    private lateinit var addressRepository: TestAddressRepository

    @Autowired
    private lateinit var idNestedRepository: TestIdNestedEntityRepository

    @Autowired
    private lateinit var testCustomerRepository: TestCustomerRepository

    @Autowired
    private lateinit var simpleRefRepository: TestSimpleRefEntityRepository

    /**
     * Access to StorageHandler to pull the actual encrypted bytes the framework stored for storage-backed fields,
     * bypassing the framework's read/decrypt path entirely. This allows us to verify the key correctness
     * against what was actually stored, not just what is in memory.
     */
    @Autowired
    private lateinit var storageHandler: StorageHandler

    /** SecureRandom instance for generating random secrets and byte arrays in tests. */
    private val secureRandom = SecureRandom()

    // -------------------------------------------------------------------------
    // @HKDFId entity — String field, correct key is the entity's own secret
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity - encrypted String inline uses entity secret, not master secret`() {
        // Given
        val secret = generateSecret()
        val originalString = "key-correctness-string-hkdf@example.com"
        val entity = TestUser().withSecret(secret).apply {
            email = originalString
        }

        // When — save() calls processFields(isWrite=true), leaving the String field Base64-encoded ciphertext in memory
        userRepository.save(entity)

        // Read the raw ciphertext string directly — no reflection needed for String fields,
        // as they are not intercepted by any aspect.
        // At this point the field holds Base64(IV + ciphertext), not the original plaintext.
        val rawCiphertext = entity.email!!

        // Sanity: must not equal the original plaintext
        assertNotEquals(
            originalString,
            rawCiphertext,
            "Field must hold ciphertext after save, not plaintext"
        )

        // Then — decrypt with the CORRECT key (entity secret) must yield the original plaintext
        val decryptedWithCorrectKey = AES256.decrypt(secret, TestUser::class.java, rawCiphertext)
        assertEquals(
            originalString,
            decryptedWithCorrectKey,
            "@HKDFId String must decrypt correctly with the entity secret"
        )

        // Then — decrypt with the WRONG key (master secret) must fail.
        // String decrypt uses the ByteArray overload internally; on wrong key it returns the
        // input bytes unchanged — which re-encode back to the same Base64 ciphertext string.
        val masterSecret = MasterSecretHolder.getMasterSecret()
        val decryptedWithWrongKey = AES256.decrypt(masterSecret, TestUser::class.java, rawCiphertext.decode64())
        assertTrue(
            decryptedWithWrongKey.contentEquals(rawCiphertext.decode64()),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // @Id entity — String field, correct key is the master secret, NOT the CID
    // -------------------------------------------------------------------------

    @Test
    fun `@Id entity - encrypted String inline uses master secret, not CID`() {
        // Given
        val cid = CID.randomCIDString()
        val originalString = "key-correctness-string-id-entity"
        val entity = TestIdEncryptEntity().withSecret(cid).apply {
            sensitiveLabel = originalString
            publicLabel = "string-key-correctness-test"
        }

        // When — save() leaves the String field as Base64-encoded ciphertext in memory
        idEncryptRepository.save(entity)

        // Read the raw ciphertext string directly — no reflection needed for String fields,
        // as they are not intercepted by any aspect.
        val rawCiphertext = entity.sensitiveLabel!!

        // Sanity: must not equal the original plaintext
        assertNotEquals(
            originalString,
            rawCiphertext,
            "Field must hold ciphertext after save, not plaintext"
        )

        val masterSecret = MasterSecretHolder.getMasterSecret()

        // Then — decrypt with the CORRECT key (master secret) must yield the original plaintext
        val decryptedWithCorrectKey = AES256.decrypt(masterSecret, TestIdEncryptEntity::class.java, rawCiphertext)
        assertEquals(
            originalString,
            decryptedWithCorrectKey,
            "@Id String must decrypt correctly with the master secret"
        )

        // Then — decrypt with the WRONG key (CID) must fail — regression check for the 1.0.4-1.0.7 bug.
        // On wrong key the ByteArray decrypt returns the input bytes unchanged.
        val decryptedWithWrongKey = AES256.decrypt(cid, TestIdEncryptEntity::class.java, rawCiphertext.decode64())
        assertTrue(
            decryptedWithWrongKey.contentEquals(rawCiphertext.decode64()),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        idEncryptRepository.deleteBySecret(cid)
    }

    // -------------------------------------------------------------------------
    // @HKDFId entity — correct key is the entity's own secret
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity - encrypted bytes inline use entity secret, not master secret`() {
        // Given — 512 bytes, well below the 1KB inline threshold
        val secret = generateSecret()
        val originalBytes = ByteArray(512).also { secureRandom.nextBytes(it) }

        val entity = TestUser().withSecret(secret).apply {
            email = "keycorrectness@example.com"
            encryptedBytes = originalBytes
        }

        // When — save() calls processFields(isWrite=true), leaving the field encrypted in memory
        userRepository.save(entity)

        // Read the raw encrypted bytes directly from the field via reflection.
        // At this point the field holds ciphertext, not plaintext.
        val rawEncryptedBytes = entity.readField<ByteArray>("encryptedBytes")

        // Sanity: the raw bytes must not equal the original plaintext
        assertFalse(
            rawEncryptedBytes.contentEquals(originalBytes),
            "Field must hold ciphertext after save, not plaintext"
        )

        // Then — decrypt with the CORRECT key (entity secret) must yield the original plaintext
        val decryptedWithCorrectKey = AES256.decrypt(secret, TestUser::class.java, rawEncryptedBytes)
        assertArrayEquals(
            originalBytes,
            decryptedWithCorrectKey,
            "@HKDFId ByteArray must decrypt correctly with the entity secret"
        )

        // Then — decrypt with the WRONG key (master secret) must fail and return the encrypted payload
        val masterSecret = MasterSecretHolder.getMasterSecret()
        val decryptedWithWrongKey = AES256.decrypt(masterSecret, TestUser::class.java, rawEncryptedBytes)
        assertFalse(
            decryptedWithWrongKey.contentEquals(originalBytes),
            "@HKDFId ByteArray must NOT decrypt with the master secret (wrong key)"
        )
        assertTrue(
            decryptedWithWrongKey.contentEquals(rawEncryptedBytes),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // @Id entity — correct key is the master secret, NOT the CID
    // -------------------------------------------------------------------------

    @Test
    fun `@Id entity - encrypted bytes inline use master secret, not CID`() {
        // Given — 512 bytes inline
        val cid = CID.randomCIDString()
        val originalBytes = ByteArray(512).also { secureRandom.nextBytes(it) }
        val entity = TestIdEncryptEntity().withSecret(cid).apply {
            sensitiveBytes = originalBytes
            publicLabel = "key-correctness-test"
        }

        // When — save() leaves the field encrypted in memory
        idEncryptRepository.save(entity)

        // Read the raw encrypted bytes directly from the field via reflection.
        // At this point the field holds ciphertext, not plaintext.
        val rawEncryptedBytes = entity.readField<ByteArray>("sensitiveBytes")

        // Sanity: must be ciphertext, not plaintext
        assertFalse(
            rawEncryptedBytes.contentEquals(originalBytes),
            "Field must hold ciphertext after save, not plaintext"
        )

        // Then — decrypt with the CORRECT key (master secret) must yield the original plaintext
        val masterSecret = MasterSecretHolder.getMasterSecret()
        val decryptedWithCorrectKey = AES256.decrypt(masterSecret, TestIdEncryptEntity::class.java, rawEncryptedBytes)
        assertArrayEquals(
            originalBytes,
            decryptedWithCorrectKey,
            "@Id ByteArray must decrypt correctly with the master secret"
        )

        // Then — decrypt with the WRONG key (CID — the entity's own secret) must fail
        // This is exactly the regression check for the 1.0.4-1.0.7 bug:
        // back then, the CID was used as the encryption key, so this line would have passed
        // and the assertion above would have failed.
        val decryptedWithCID = AES256.decrypt(cid, TestIdEncryptEntity::class.java, rawEncryptedBytes)
        assertFalse(
            decryptedWithCID.contentEquals(originalBytes),
            "@Id ByteArray must NOT decrypt with the CID (wrong key — regression check for the 1.0.4-1.0.7 bug)"
        )
        assertTrue(
            decryptedWithCID.contentEquals(rawEncryptedBytes),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        idEncryptRepository.deleteBySecret(cid)
    }

    // -------------------------------------------------------------------------
    // @HKDFId large field (above threshold) — stored in storage, same key rule applies
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity - large encrypted bytes (above threshold) also use entity secret`() {
        // Given — 256KB, above the 1KB threshold (stored in storage backend)
        val secret = generateSecret()
        val originalBytes = createSampleBytes(256)
        val entity = TestUser().withSecret(secret).apply {
            email = "large@example.com"
            encryptedBytes = originalBytes
        }

        // When — save() stores the encrypted bytes in the storage backend
        userRepository.save(entity)

        // Pull the actual ciphertext the framework stored — same technique as the last test.
        // For storage-backed fields, the in-memory field holds a reference after save, not ciphertext.
        // We go through StorageHandler to read the raw encrypted bytes the framework actually wrote.
        val storage = storageHandler.getStorageForField(TestUser::class.java, "encryptedBytes")
        val field = storageHandler.getField(TestUser::class.java, "encryptedBytes")

        val referenceBytes = storageHandler.getReferenceBytes(entity, "encryptedBytes")
        assertNotNull(referenceBytes, "Storage reference bytes must not be null for large field")

        val storageRef = storage.createReference(referenceBytes)
        assertNotNull(storageRef, "Storage reference must be creatable from reference bytes")

        val bytesStoredEncrypted = storage.read(field.metadata, storageRef!!)
        assertNotNull(bytesStoredEncrypted, "Storage must return the encrypted bytes for the field")

        // Smart-cast: assertNotNull above guarantees non-null, but the compiler still sees ByteArray?
        // from the IStorage<*> return type. This cast narrows to ByteArray, avoiding !! on every use below.
        // A ClassCastException here would mean IStorage.read returned a non-ByteArray — a framework
        // contract violation that is correctly fatal.
        bytesStoredEncrypted as ByteArray

        // Sanity: what is in storage must not equal the original plaintext
        assertFalse(
            bytesStoredEncrypted.contentEquals(originalBytes),
            "Storage must hold ciphertext, not plaintext"
        )

        // Then — decrypt with the CORRECT key (entity secret) must yield the original plaintext
        val masterSecret = MasterSecretHolder.getMasterSecret()
        val decryptedWithCorrectKey = AES256.decrypt(secret, TestUser::class.java, bytesStoredEncrypted)
        assertArrayEquals(
            originalBytes,
            decryptedWithCorrectKey,
            "Large @HKDFId ByteArray must decrypt correctly with the entity secret"
        )

        // Then — decrypt with the WRONG key (master secret) must fail
        val decryptedWithWrongKey = AES256.decrypt(masterSecret, TestUser::class.java, bytesStoredEncrypted)
        assertFalse(
            decryptedWithWrongKey.contentEquals(originalBytes),
            "Large @HKDFId ByteArray must NOT decrypt with the master secret (wrong key)"
        )
        assertTrue(
            decryptedWithWrongKey.contentEquals(bytesStoredEncrypted),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        userRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // @Id large field (above threshold)
    // -------------------------------------------------------------------------

    @Test
    fun `@Id entity - large encrypted bytes (above threshold) also use master secret`() {
        // Given — 256KB, above the 1KB threshold (stored in storage backend)
        val cid = CID.randomCIDString()
        val originalBytes = createSampleBytes(256)
        val entity = TestIdEncryptEntity().withSecret(cid).apply {
            sensitiveBytes = originalBytes
            publicLabel = "large-key-correctness"
        }

        // When — save() stores the encrypted bytes in the storage backend
        idEncryptRepository.save(entity)

        // Pull the actual ciphertext the framework stored — same technique as the last test.
        val storage = storageHandler.getStorageForField(TestIdEncryptEntity::class.java, "sensitiveBytes")
        val field = storageHandler.getField(TestIdEncryptEntity::class.java, "sensitiveBytes")

        val referenceBytes = storageHandler.getReferenceBytes(entity, "sensitiveBytes")
        assertNotNull(referenceBytes, "Storage reference bytes must not be null for large field")

        val storageRef = storage.createReference(referenceBytes)
        assertNotNull(storageRef, "Storage reference must be creatable from reference bytes")

        val bytesStoredEncrypted = storage.read(field.metadata, storageRef!!)
        assertNotNull(bytesStoredEncrypted, "Storage must return the encrypted bytes for the field")

        // Smart-cast: assertNotNull above guarantees non-null, but the compiler still sees ByteArray?
        // from the IStorage<*> return type. This cast narrows to ByteArray, avoiding !! on every use below.
        // A ClassCastException here would mean IStorage.read returned a non-ByteArray — a framework
        // contract violation that is correctly fatal.
        bytesStoredEncrypted as ByteArray

        // Sanity: what is in storage must not equal the original plaintext
        assertFalse(
            bytesStoredEncrypted.contentEquals(originalBytes),
            "Storage must hold ciphertext, not plaintext"
        )

        val masterSecret = MasterSecretHolder.getMasterSecret()

        // Then — decrypt with the CORRECT key (master secret) must yield the original plaintext
        val decryptedWithCorrectKey = AES256.decrypt(masterSecret, TestIdEncryptEntity::class.java, bytesStoredEncrypted)
        assertArrayEquals(
            originalBytes,
            decryptedWithCorrectKey,
            "Large @Id ByteArray must decrypt correctly with the master secret"
        )

        // Then — decrypt with the WRONG key (CID) must fail — regression check for the 1.0.4-1.0.7 bug
        val decryptedWithWrongKey = AES256.decrypt(cid, TestIdEncryptEntity::class.java, bytesStoredEncrypted)
        assertFalse(
            decryptedWithWrongKey.contentEquals(originalBytes),
            "Large @Id ByteArray must NOT decrypt with the CID (wrong key — regression check for the 1.0.4-1.0.7 bug)"
        )
        assertTrue(
            decryptedWithWrongKey.contentEquals(bytesStoredEncrypted),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        idEncryptRepository.deleteBySecret(cid)
    }

    // -------------------------------------------------------------------------
    // @HKDFId entity — List<String> field, correct key is the entity's own secret
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity - encrypted List(String) inline uses entity secret, not master secret`() {
        // Given
        val secret = generateSecret()
        val originalList = listOf("alpha", "beta", "gamma")
        val entity = TestComplexEntity().withSecret(secret).apply {
            stringList = originalList.toMutableList()
        }

        // When — save() encrypts each element individually, leaving the list holding
        // Base64(IV + ciphertext) per element in memory — same as a String field, but per element.
        complexRepository.save(entity)

        // Read the list directly — List<String> fields are not intercepted by any aspect.
        val rawList = entity.stringList

        // Sanity: every element must be ciphertext, not plaintext
        rawList.forEachIndexed { i, raw ->
            assertNotEquals(originalList[i], raw, "Element $i must hold ciphertext after save, not plaintext")
        }

        val masterSecret = MasterSecretHolder.getMasterSecret()

        // Then — each element decrypts with the CORRECT key (entity secret) must yield the original
        rawList.forEachIndexed { i, raw ->
            val decryptedWithCorrectKey = AES256.decrypt(secret, TestComplexEntity::class.java, raw)
            assertEquals(
                originalList[i],
                decryptedWithCorrectKey,
                "@HKDFId List<String> element $i must decrypt correctly with the entity secret"
            )
        }

        // Then — each element decrypted with the WRONG key (master secret) must fail
        rawList.forEach { raw ->
            val decryptedWithWrongKey = AES256.decrypt(masterSecret, TestComplexEntity::class.java, raw.decode64())
            assertTrue(
                decryptedWithWrongKey.contentEquals(raw.decode64()),
                "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
            )
        }

        // Cleanup
        complexRepository.deleteBySecret(secret)
    }

    // -------------------------------------------------------------------------
    // @Id entity — List<String> field, correct key is the master secret, NOT the CID
    // -------------------------------------------------------------------------

    @Test
    fun `@Id entity - encrypted List(String) inline uses master secret, not CID`() {
        // Given
        val cid = CID.randomCIDString()
        val originalList = listOf("alpha", "beta", "gamma")
        val entity = TestIdEncryptEntity().withSecret(cid).apply {
            sensitiveList = originalList.toMutableList()
            publicLabel = "list-key-correctness-test"
        }

        // When — save() encrypts each element individually
        idEncryptRepository.save(entity)

        // Read the list directly — List<String> fields are not intercepted by any aspect.
        val rawList = entity.sensitiveList

        // Sanity: every element must be ciphertext, not plaintext
        rawList.forEachIndexed { i, raw ->
            assertNotEquals(originalList[i], raw, "Element $i must hold ciphertext after save, not plaintext")
        }

        val masterSecret = MasterSecretHolder.getMasterSecret()

        // Then — each element decrypts with the CORRECT key (master secret) must yield the original
        rawList.forEachIndexed { i, raw ->
            val decryptedWithCorrectKey = AES256.decrypt(masterSecret, TestIdEncryptEntity::class.java, raw)
            assertEquals(
                originalList[i],
                decryptedWithCorrectKey,
                "@Id List<String> element $i must decrypt correctly with the master secret"
            )
        }

        // Then — each element decrypted with the WRONG key (CID) must fail —
        // regression check for the 1.0.4-1.0.7 bug
        rawList.forEach { raw ->
            val decryptedWithWrongKey = AES256.decrypt(cid, TestIdEncryptEntity::class.java, raw.decode64())
            assertTrue(
                decryptedWithWrongKey.contentEquals(raw.decode64()),
                "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
            )
        }

        // Cleanup
        idEncryptRepository.deleteBySecret(cid)
    }

    // -------------------------------------------------------------------------
    // @HKDFId entity — encryptableFieldMap, correct key is the entity's own secret
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity - encryptableFieldMap value is encrypted with entity secret, not master secret`() {
        // Given — a parent TestContainer with one nested TestItem (@PartOf)
        val parentSecret = generateSecret()
        val itemSecret = generateSecret()

        val item = TestItem().withSecret(itemSecret).apply { name = "nested-item" }
        val container = TestContainer().withSecret(parentSecret).apply {
            title = "map-key-correctness"
            items.add(item)
        }

        // When — save() encrypts every value in encryptableListFieldMap with the parent entity secret
        containerRepository.save(container)

        // Read encryptableListFieldMap directly via reflection — bypasses any decryption path
        val encryptableListFieldMap = container.readField<MutableMap<String, MutableList<String>>>("encryptableListFieldMap")
        val rawEncryptedSecrets = encryptableListFieldMap["items"]
        assertNotNull(rawEncryptedSecrets, "encryptableListFieldMap must contain an entry for 'items' after save")
        assertTrue(rawEncryptedSecrets!!.isNotEmpty(), "The items list in the map must not be empty")

        val rawEncryptedItemSecret = rawEncryptedSecrets[0]

        // Sanity: the encrypted value must not equal the original item secret
        assertNotEquals(
            itemSecret,
            rawEncryptedItemSecret,
            "encryptableListFieldMap value must hold ciphertext, not the plaintext secret"
        )

        val masterSecret = MasterSecretHolder.getMasterSecret()

        // Then — decrypt with the CORRECT key (parent entity secret) must yield the item's secret
        val decryptedWithCorrectKey = AES256.decrypt(parentSecret, TestContainer::class.java, rawEncryptedItemSecret)
        assertEquals(
            itemSecret,
            decryptedWithCorrectKey,
            "@HKDFId encryptableListFieldMap value must decrypt correctly with the parent entity secret"
        )

        // Then — decrypt with the WRONG key (master secret) must fail
        val decryptedWithWrongKey = AES256.decrypt(masterSecret, TestContainer::class.java, rawEncryptedItemSecret.decode64())
        assertTrue(
            decryptedWithWrongKey.contentEquals(rawEncryptedItemSecret.decode64()),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup
        containerRepository.deleteBySecret(parentSecret)
    }

    // -------------------------------------------------------------------------
    // @HKDFId entity — encryptableFieldMap (single nested), correct key is entity secret
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity - encryptableFieldMap single nested value is encrypted with entity secret, not master secret`() {
        // Given — a TestCustomer (@HKDFId) with a single @PartOf TestAddress nested field
        val parentSecret = generateSecret()
        val addressSecret = generateSecret()

        val address = TestAddress().withSecret(addressSecret).apply {
            street = "123 Key Correctness Ave"
            city = "Testville"
        }
        val customer = TestCustomer().withSecret(parentSecret).apply {
            name = "single-map-key-correctness"
            billingAddress = address
        }

        // When — save() encrypts the value in encryptableFieldMap with the parent entity secret
        testCustomerRepository.save(customer)

        // Read encryptableFieldMap directly via reflection — bypasses any decryption path
        val encryptableFieldMap = customer.readField<MutableMap<String, String>>("encryptableFieldMap")
        val rawEncryptedAddressSecret = encryptableFieldMap["billingAddress"]
        assertNotNull(rawEncryptedAddressSecret, "encryptableFieldMap must contain an entry for 'billingAddress' after save")

        // Sanity: the encrypted value must not equal the original address secret
        assertNotEquals(
            addressSecret,
            rawEncryptedAddressSecret,
            "encryptableFieldMap value must hold ciphertext, not the plaintext secret"
        )

        val masterSecret = MasterSecretHolder.getMasterSecret()

        // Then — decrypt with the CORRECT key (parent entity secret) must yield the address's secret
        val decryptedWithCorrectKey = AES256.decrypt(parentSecret, TestCustomer::class.java, rawEncryptedAddressSecret!!)
        assertEquals(
            addressSecret,
            decryptedWithCorrectKey,
            "@HKDFId encryptableFieldMap value must decrypt correctly with the parent entity secret"
        )

        // Then — decrypt with the WRONG key (master secret) must fail
        val decryptedWithWrongKey = AES256.decrypt(masterSecret, TestCustomer::class.java, rawEncryptedAddressSecret.decode64())
        assertTrue(
            decryptedWithWrongKey.contentEquals(rawEncryptedAddressSecret.decode64()),
            "On wrong key, AES256.decrypt must return the encrypted payload unchanged"
        )

        // Cleanup — deleteBySecret on parent cascades @PartOf children
        testCustomerRepository.deleteBySecret(parentSecret)
    }

    // -------------------------------------------------------------------------
    // @Id entity — encryptableListFieldMap stores plaintext ID (not encrypted)
    // -------------------------------------------------------------------------

    @Test
    fun `@Id entity - encryptableListFieldMap stores nested entity ID as plaintext, not encrypted`() {
        // Given — a @Id parent TestIdNestedEntity with nested TestItem list (@PartOf)
        //
        // For @Id parents, isolated=false → encryptableListFieldMap values are NEVER encrypted.
        // The nested entity's ID is stored as a plaintext string reference.
        val cid = CID.randomCIDString()
        val itemSecret = generateSecret()

        val item = TestItem().withSecret(itemSecret).apply { name = "id-nested-item" }
        val parent = TestIdNestedEntity().withSecret(cid).apply {
            label = "id-list-map-plaintext"
            nestedItems.add(item)
        }

        // When
        idNestedRepository.save(parent)

        // After save the nested item has its ID assigned — that is what was stored in the map.
        val nestedItemId = item.id?.toBase64Url()
        assertNotNull(nestedItemId, "Nested item must have its ID assigned after parent save")

        // Read encryptableListFieldMap directly via reflection
        val encryptableListFieldMap = parent.readField<MutableMap<String, MutableList<String>>>("encryptableListFieldMap")
        val rawValues = encryptableListFieldMap["nestedItems"]
        assertNotNull(rawValues, "encryptableListFieldMap must contain an entry for 'nestedItems' after save")
        assertTrue(rawValues!!.isNotEmpty(), "The nestedItems list in the map must not be empty")

        val storedValue = rawValues[0]

        // Then — the stored value must be the nested entity's ID in plaintext (not encrypted)
        assertEquals(
            nestedItemId,
            storedValue,
            "@Id parent: encryptableListFieldMap must store the nested entity's ID as plaintext — not encrypted"
        )

        // Cleanup
        idNestedRepository.deleteBySecret(cid, true)
    }

    // -------------------------------------------------------------------------
    // @Id entity — encryptableFieldMap stores plaintext ID (not encrypted)
    // -------------------------------------------------------------------------

    @Test
    fun `@Id entity - encryptableFieldMap stores nested entity ID as plaintext, not encrypted`() {
        // Given — a @Id parent TestIdNestedEntity with a single nested TestAddress (@PartOf)
        //
        // For @Id parents, isolated=false → encryptableFieldMap values are NEVER encrypted.
        // The nested entity's ID is stored as a plaintext string reference.
        val cid = CID.randomCIDString()
        val addressSecret = generateSecret()

        val address = TestAddress().withSecret(addressSecret).apply {
            street = "456 Plaintext Blvd"
            city = "Reftown"
        }
        val parent = TestIdNestedEntity().withSecret(cid).apply {
            label = "id-single-map-plaintext"
            nestedAddress = address
        }

        // When
        idNestedRepository.save(parent)

        // After save the nested address has its ID assigned — that is what the framework stored.
        val nestedAddressId = address.id?.toBase64Url()
        assertNotNull(nestedAddressId, "Nested address must have its ID assigned after parent save")

        // Read encryptableFieldMap directly via reflection
        val encryptableFieldMap = parent.readField<MutableMap<String, String>>("encryptableFieldMap")
        val storedValue = encryptableFieldMap["nestedAddress"]
        assertNotNull(storedValue, "encryptableFieldMap must contain an entry for 'nestedAddress' after save")

        // Then — the stored value must be the nested entity's ID in plaintext (not encrypted)
        assertEquals(
            nestedAddressId,
            storedValue,
            "@Id parent: encryptableFieldMap must store the nested entity's ID as plaintext — not encrypted"
        )

        // Cleanup — deleteBySecret on the parent cascades @PartOf children
        idNestedRepository.deleteBySecret(cid, true)
    }

    // -------------------------------------------------------------------------
    // @HKDFId entity + @SimpleReference — encryptableFieldMap stores plaintext ID
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity with @SimpleReference - encryptableFieldMap stores nested entity ID as plaintext, not secret`() {
        // Given — a @HKDFId parent TestSimpleRefEntity with a @SimpleReference @PartOf child.
        //
        // Even though the parent is isolated (@HKDFId), @SimpleReference overrides the default
        // behaviour: instead of storing the nested entity's secret (encrypted), the map stores
        // the nested entity's ID as plaintext — identical to the @Id parent path.
        val parentSecret = generateSecret()
        val childSecret = generateSecret()

        val child = TestAddress().withSecret(childSecret).apply {
            street = "789 Simple Ref St"
            city = "Plainville"
        }
        val parent = TestSimpleRefEntity().withSecret(parentSecret).apply {
            name = "simple-ref-single"
            simpleChild = child
        }

        // When
        simpleRefRepository.save(parent)

        // After save the nested child has its ID assigned
        val childId = child.id?.toBase64Url()
        assertNotNull(childId, "Nested child must have its ID assigned after parent save")

        // Read encryptableFieldMap directly via reflection
        val encryptableFieldMap = parent.readField<MutableMap<String, String>>("encryptableFieldMap")
        val storedValue = encryptableFieldMap["simpleChild"]
        assertNotNull(storedValue, "encryptableFieldMap must contain an entry for 'simpleChild' after save")

        // Then — the stored value must be the nested entity's ID in plaintext
        assertEquals(
            childId,
            storedValue,
            "@HKDFId + @SimpleReference: encryptableFieldMap must store the nested entity's ID as plaintext — not the secret, not encrypted"
        )

        // Verify it is NOT the secret — for @HKDFId entities the secret and ID are different
        assertNotEquals(
            childSecret,
            storedValue,
            "@SimpleReference must store the ID, not the secret"
        )

        // Cleanup
        simpleRefRepository.deleteBySecret(parentSecret)
    }

    // -------------------------------------------------------------------------
    // @HKDFId entity + @SimpleReference — encryptableListFieldMap stores plaintext IDs
    // -------------------------------------------------------------------------

    @Test
    fun `@HKDFId entity with @SimpleReference - encryptableListFieldMap stores nested entity IDs as plaintext, not secrets`() {
        // Given — a @HKDFId parent TestSimpleRefEntity with a @SimpleReference @PartOf list.
        //
        // Same rule: @SimpleReference forces storage of nested entity IDs (not secrets),
        // and the values are not encrypted — even on an isolated parent.
        val parentSecret = generateSecret()
        val itemSecret = generateSecret()

        val item = TestItem().withSecret(itemSecret).apply { name = "simple-ref-item" }
        val parent = TestSimpleRefEntity().withSecret(parentSecret).apply {
            name = "simple-ref-list"
            simpleChildren.add(item)
        }

        // When
        simpleRefRepository.save(parent)

        // After save the nested item has its ID assigned
        val itemId = item.id?.toBase64Url()
        assertNotNull(itemId, "Nested item must have its ID assigned after parent save")

        // Read encryptableListFieldMap directly via reflection
        val encryptableListFieldMap = parent.readField<MutableMap<String, MutableList<String>>>("encryptableListFieldMap")
        val storedValues = encryptableListFieldMap["simpleChildren"]
        assertNotNull(storedValues, "encryptableListFieldMap must contain an entry for 'simpleChildren' after save")
        assertTrue(storedValues!!.isNotEmpty(), "The simpleChildren list in the map must not be empty")

        val storedValue = storedValues[0]

        // Then — the stored value must be the nested entity's ID in plaintext
        assertEquals(
            itemId,
            storedValue,
            "@HKDFId + @SimpleReference: encryptableListFieldMap must store the nested entity's ID as plaintext — not the secret, not encrypted"
        )

        // Verify it is NOT the secret
        assertNotEquals(
            itemSecret,
            storedValue,
            "@SimpleReference must store the ID, not the secret"
        )

        // Cleanup
        simpleRefRepository.deleteBySecret(parentSecret)
    }
}


