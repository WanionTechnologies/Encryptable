# Encryptable Test Suite

> **Note:** `projectcards` was the code name for this framework during early development and is retained as the legacy database name and package for tests.

Comprehensive test coverage for Encryptable MongoDB encryption framework.

## Test Files

### 0. **ApplicationTests.kt** - Context Load

- ✅ Verifies that the Spring application context loads successfully

**1 test** ensuring the test environment is correctly set up.

### 1. **EncryptableBasicTest.kt** - Fundamental Operations

- ✅ Save and retrieve entities with encrypted fields
- ✅ Deterministic ID generation from secrets
- ✅ Entity existence checks
- ✅ Null handling for encrypted fields
- ✅ Automatic field updates (change detection)
- ✅ Batch operations (saveAll, findAllBySecrets, deleteAllBySecrets)
- ✅ Alternative retrieval syntax (`.asSecretOf<T>()`)

**8 tests** covering basic CRUD operations and encryption/decryption.

### 2. **EncryptableNestedEntitiesTest.kt** - Relationships

- ✅ Save and retrieve nested Encryptable entities
- ✅ Cascade delete with `@PartOf` annotation
- ✅ Reference without cascade (no `@PartOf`)
- ✅ Update nested entity fields
- ✅ Handle null nested entities

**5 tests** covering composition relationships and cascade behavior.

### 3. **EncryptableGridFSTest.kt** - Large Binary Files

- ✅ Store small binaries in document (<1KB)
- ✅ Store large binaries in GridFS (>1KB)
- ✅ Lazy loading of GridFS files
- ✅ Encrypted vs unencrypted GridFS storage
- ✅ Update large binary fields
- ✅ GridFS cleanup on entity delete
- ✅ Transition from small to large file
- ✅ Handle null binary fields
- ✅ Multiple large files in same entity

**9 tests** covering GridFS integration and lazy loading.

### 4. **EncryptableListTest.kt** - Collection Management

- ✅ Save and retrieve lists of items
- ✅ Add items automatically (auto-persist)
- ✅ Remove items with cascade delete (`@PartOf`)
- ✅ Remove without cascade (no `@PartOf`)
- ✅ Clear entire list
- ✅ Lazy load list items on iteration
- ✅ Handle empty lists
- ✅ Modify items within list
- ✅ Get list size correctly

**8 tests** covering `EncryptableList` functionality.

### 5. **EncryptableChangeDetectionTest.kt** - Partial Updates

- ✅ Detect single field change
- ✅ Detect multiple field changes
- ✅ No update when no fields changed
- ✅ Handle binary field changes
- ✅ Compute field hashes correctly
- ✅ Use `touch()` for audit fields
- ✅ Set field to null
- ✅ Change null to value
- ✅ Efficient large binary handling
- ✅ Track changes across multiple retrievals

**10 tests** covering field-level change detection and optimization.

### 6. **EncryptableIDStrategyTest.kt** - ID Generation

- ✅ HKDF deterministic ID generation
- ✅ HKDF encryption support
- ✅ Direct ID strategy (secret as CID)
- ✅ HKDF with high entropy secrets
- ✅ Different secrets generate different IDs
- ✅ Retrieve by HKDF derived ID
- ✅ HKDF consistency across restarts

**7 tests** covering both ID strategies (`@Id` and `@HKDFId`).

### 7. **EncryptableIntegrationTest.kt** - Complex Scenarios

- ✅ Multiple encrypted field types
- ✅ Encrypt nested objects with annotations
- ✅ Concurrent operations on different entities
- ✅ Empty string list handling
- ✅ String list modifications
- ✅ Data integrity across multiple updates
- ✅ All null encrypted fields
- ✅ Binary data with special byte values
- ✅ Rapid successive updates
- ✅ Unicode and special characters
- ✅ Consistency after failed retrieval

**11 tests** covering edge cases and advanced patterns.

### 8. **EncryptableUnsavedCleanupTest.kt** - Unsaved Entity Handling

- ✅ Cleanup unsaved entity with no nested entities
- ✅ Do not cleanup entity that was saved
- ✅ Cleanup unsaved entity with nested @PartOf entities
- ✅ Cleanup unsaved entity with list of @PartOf entities
- ✅ Cleanup unsaved entity with associated GridFS files
- ✅ Prevent orphaned resources
- ✅ Verify cascade delete for nested/list/GridFS

**7 tests** covering unsaved entity handling and cleanup.

### 9. **EncryptableSecretRotationTest.kt** - Secret Rotation

- ✅ Rotate a user's secret using `rotateSecret`
- ✅ Ensure user is not accessible with the old secret after rotation
- ✅ Ensure user is accessible with the new secret and data is preserved

**3 tests** covering secret rotation and data integrity after secret update.

### 10. **EncryptableGridFSRotationTest.kt** - Secret Rotation with GridFS Files

- ✅ Rotate secret for entities with large GridFS files (>1KB)
- ✅ Ensure entity is not accessible with the old secret after rotation
- ✅ Ensure entity is accessible with the new secret and all file data is preserved (both encrypted and unencrypted fields)

**3 tests** covering secret rotation and data integrity for entities with large GridFS files.

### 11. **CryptoPropertiesTest.kt** - Crypto Properties (Unit)

- ✅ AES‑GCM round‑trip returns original plaintext
- ✅ Tamper behavior (decrypt returns still‑encrypted payload on failure)
- ✅ IV uniqueness across runs
- ✅ HKDF determinism, namespacing, output lengths (16/32 bytes)
- ✅ Multiple encryptions → distinct ciphertexts; all decrypt back to the same plaintext
- ✅ Decrypt with wrong secret returns still‑encrypted payload (not plaintext)

**6 tests** verifying cryptographic properties (no DB required).

## Total Coverage

- **74 test cases** across 12 test files
- All major framework features tested
- Edge cases and error scenarios covered
- Integration tests for complex workflows

## Running the Tests

### Run all tests:

```bash
./gradlew test
```

### Run specific test class:

```bash
./gradlew test --tests EncryptableBasicTest
./gradlew test --tests EncryptableGridFSTest
./gradlew test --tests CryptoPropertiesTest
```

### Run with coverage report:

```bash
./gradlew test jacocoTestReport
```

## Prerequisites

- MongoDB running locally (default: `localhost:27017`)
- Test database: Tests will use the configured database
- GridFS support enabled

> Note: `CryptoPropertiesTest` does not require MongoDB.

## Test Entities

All test entities use the `test_*` collection naming convention to avoid conflicts with production data:

- `test_users`
- `test_addresses`
- `test_documents`
- `test_items`
- `test_containers`
- etc.

## Test Patterns

### Secret Generation

Secure random secrets for testing:

```kotlin
private fun generateSecret(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
```

### Cleanup

Each test cleans up after itself:

```kotlin
// Cleanup
repository.deleteBySecret(secret)
```

## Key Testing Principles

1. **Isolation**: Each test is independent and cleans up
2. **Determinism**: Tests produce consistent results
3. **Coverage**: All code paths and edge cases tested
4. **Real MongoDB**: Tests use actual MongoDB, not mocks
5. **Comprehensive**: Tests cover happy paths and error scenarios

## CI/CD Integration

Tests are designed to run in CI/CD pipelines:

- No hard-coded dependencies
- Clean state before/after tests
- Parallel test execution safe
- Environment-agnostic

## Test Data Safety

- All test collections prefixed with `test_`
- Each test generates unique secrets
- Cleanup ensures no data leakage
- No production data touched

> **Note on Secret Sanitization:** For tests, secrets are not "sanitized" immediately.\
> Sanitization occurs only after the repository thread local is cleared in `EncryptableInterceptor.afterCompletion`.\
> **This is intentional**: since tests need to reuse the same secret to retrieve test entities, immediate clearing would require copying secrets, which is a risky practice.\
> Copying secrets increases the number of memory locations containing sensitive data, and these copies would not be automatically cleared at the end of the request, potentially leaving secrets in memory longer than intended.\
> This approach avoids encouraging unsafe secret management patterns.

---

**Test Suite Status**: ✅ Comprehensive coverage of all framework features.