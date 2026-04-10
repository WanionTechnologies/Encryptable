# Changelog

All notable changes to Encryptable will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Semantic Versioning Policy

- **Major (X.0.0)** - Breaking changes, API redesigns (rare, with [migration guides](docs/MIGRATING_FROM_OTHER_VERSIONS.md))
- **Minor (1.X.0)** - New features, backward compatible
- **Patch (1.0.X)** - Bug fixes, security patches, no API changes

---

## Version History

- **1.0.0** (2025-12-12) - Initial release
- **1.0.1** (2025-12-14) - Aspect Application Reliability
- **1.0.2** (2025-12-17) - Reverted Change to Aspect Application Reliability, see details below
- **1.0.3** (2025-12-20) - Documentation: Zero-Knowledge → Transient Knowledge terminology
- **1.0.4** (2026-01-07) - Master Secret Support for @Id Entities
- **1.0.5** (2026-01-18) - Security: Remove Cross-Reference Leak Vectors
- **1.0.6** (2026-01-24) - Performance: Code Optimizations & Bulk Updates
- **1.0.7** (2026-01-30) - Polymorphic Relationships Support
- **1.0.8** (2026-02-27) - Major Improvements & Critical Security Fixes
- **1.0.9** (2026-03-07) - Minor Fix: Nested Entity IDs Incorrectly Encrypted on `@Id` Parents & Major Test Expansion
- **1.1.0** (2026-03-19) - ⛔ Incompatible with 1.0.x: BSON Binary subtype `0x04`→custom subtype `128` (user-defined), HKDF context key derivation fix & storage threshold minimum lowered to 1KB (default remains 16KB). No migration path — fresh database required.
- **1.2.0** (2026-04-02) - 🔐 Enhanced Security: Master Secret Validation (74 chars), @HKDFId Secrets (48 chars), Read-Only Entity Protection, @Sliced Reference Header (8-byte Size Prefix).
- **1.2.1** (2026-04-07) - 🔄 Patch Release: Stability, Documentation Improvements, No Breaking Changes.
- **1.2.2** (2026-04-09) - 🔒 Security Patch: Critical Rotation Fixes & Memory Hygiene.

---
## [1.0.0] - 2025-12-12 (Initial Release)

### 🎉 Initial Release

Encryptable 1.0.0 marks the first release of the framework, providing production-ready request-scoped (transient) knowledge encryption and ORM-like features for MongoDB.

> **Why is Encryptable a framework and not just a library?** See [FAQ: Why is Encryptable a "Framework"?](docs/FAQ.md#-why-is-encryptable-called-a-framework-instead-of-a-library)

### Core Features

#### 🔐 Request-Scoped (Transient) Knowledge Cryptographic Architecture
- **AES-256-GCM encryption** for field-level data protection
- **HKDF-based deterministic CID generation** (RFC 5869)
- **Per-user cryptographic isolation** - each user's data encrypted with their own derived keys
- **Cryptographic addressing for database ORM** - stateless, request-scoped (transient) knowledge data access without username→ID mapping tables (novel application of content-addressable principles to relational data modeling)
- **No password storage** - server cannot reconstruct, reset, or access user data

#### 🗄️ ORM-Like Features for MongoDB
- **Relationship management** - One-to-One, One-to-Many, Many-to-Many associations
- **Cascade delete** - Automatic cleanup of related entities
- **Referential integrity** - Maintains data consistency across relationships
- **Lazy loading** - Efficient relationship loading on demand
- **Change detection** - Intelligent tracking of field modifications via hashCode() comparison

#### 🆔 CID (Compact ID)
- **22-character URL-safe identifiers** - Compact alternative to UUID (36 characters)
- **128-bit entropy** - Same security as UUID with 39% shorter representation
- **Deterministic IDs (@HKDFId)** - Derived from user secrets via HKDF for cryptographic addressing
- **Automatic entropy validation** - All random secrets and CIDs validated with Shannon entropy (≥3.5 bits/char) and uniqueness checking (≥25%), automatically regenerating if insufficient entropy is detected
- **Collision resistance** - 2^32× better than MongoDB ObjectId (585,000 years at 1M ops/sec for 50% collision probability)

#### 🧼 Memory Hygiene
- **Thread-local isolation** - Per-request memory management
- **Automatic wiping** - All secrets and decrypted content is cleared from memory at request end

#### 📁 Encrypted GridFS
- **Automatic file encryption** - Seamless integration with MongoDB GridFS
- **Lazy loading** - Files loaded only when accessed
- **Per-user isolation** - Each user's files encrypted with their own keys

#### 🛠️ Developer Experience
- **Kotlin-first API** - Idiomatic Kotlin with extension functions
- **Spring Boot integration** - Auto-configuration and starter module
- **AspectJ weaving** - Transparent encryption/decryption via AOP
- **Minimal annotations** - `@EnableEncryptable`, `@HKDFId`, `@Encrypt`, `@PartOf`

### Technical Stack
- **Kotlin 2.2.21** - Modern JVM language
- **Spring Boot 4.0.0** - Framework integration
- **MongoDB** - Document database with Spring Data MongoDB
- **AspectJ 1.9.22** - Aspect-oriented programming for transparent crypto
- **HKDF (at.favre.lib:hkdf:2.0.0)** - RFC 5869 key derivation
- **Java 21+** - Virtual threads for concurrency

### Documentation
- **30+ comprehensive docs** covering architecture, security, compliance, limitations, and concepts.
- **6 working examples** demonstrating usage patterns
- **AI-assisted security analysis** (not a substitute for professional audit)
- **Transparent limitations** - Honest about constraints and appropriate use cases

### Testing
- **74 passing tests** covering core functionality, encryption, relationships, GridFS, and edge cases
- **Test runtime: ~5 seconds** - Fast feedback loop

### Known Limitations

See [Limitations](docs/LIMITATIONS.md) for complete details on all known limitations and constraints.

- ⚠️ **No professional security audit yet** - Suitable for personal projects, startups, and general web apps; requires audit for regulated industries
- **Java 21+ required** - Virtual threads dependency
- **No Kotlin coroutines support** - ThreadLocal incompatibility
- **No GraalVM native image** - AspectJ limitation
- **Kotlin-first design** - Java users have limited access to extension functions
- **Recommended for new systems** - Legacy migrations possible but complex
- **Encrypted fields not queryable** - Must decrypt in-memory
- **CID-only identifiers** - No UUID, Long, or other ID types
- **No soft delete** - Hard deletion only (right to be forgotten)

### License
MIT License - Free and open-source forever

---

## [1.0.1] - 2025-12-14

### Aspect Application Reliability

- Improved framework configuration to ensure all aspects are reliably applied in user projects.

---

## [1.0.2] - 2025-12-17 (not published)

### Aspect Application Reliability

- Turns out the changes in 1.0.1 were not enough to guarantee reliable aspect application.
- Improved documentation around Gradle setup to ensure aspects are always applied correctly.
- If you were affected by aspect application issues in 1.0.0 or 1.0.1, please follow the updated Gradle configuration instructions in [Prerequisites](docs/PREREQUISITES.md) to resolve the issue.

> **Help needed**: If you know a better way to ensure aspects are always applied correctly transparently to the user, please open a PR or issue to discuss!

---

## [1.0.3] - 2025-12-20

### Documentation: Zero-Knowledge → Transient Knowledge terminology

- All documentation references to "zero-knowledge" or "zero-knowledge architecture" have been removed or replaced with the correct designation: "Transient Knowledge" (request-scoped knowledge).
- Added a new document [Not Exactly Zero-Knowledge](docs/NOT_EXACTLY_ZERO_KNOWLEDGE.md) for full explanation and apology.
- No code changes in this release.

---

## [1.0.4] - 2026-01-07

### 🔑 Master Secret Support for @Id Entities

This release introduces **master secret support**, enabling encryption for entities with standard `@Id` annotations. Previously, only `@HKDFId` entities could use `@Encrypt` fields.

#### Added

- **Master Secret Configuration**: New `MasterSecretHolder` class for managing the master secret
  - Support for environment variables (`ENCRYPTABLE_MASTER_SECRET`)
  - Support for application properties (`encryptable.master.secret`)
  - Programmatic configuration via `MasterSecretHolder.setMasterSecret()`
- **@Id Entity Encryption**: Entities with `@Id` can now use `@Encrypt` fields (requires master secret configuration)
- **Cryptographic Isolation Protection**: @Id entities now store only IDs (not secrets) when referencing @HKDFId entities
  - Prevents secret leakage if master secret is compromised
  - Maintains cryptographic isolation between entity types
- **Automatic Migration**: Legacy data with secret-based references is automatically converted to ID-only references during entity loading
  - Transparent migration - no manual intervention required
  - Gradual rollout as entities are loaded and saved
- **New Documentation**: Added [HKDFID_VS_ID.md](docs/HKDFID_VS_ID.md) explaining the differences between @HKDFId and @Id entities

#### Changed

- **Enhanced Security Model**: @HKDFId entities remain completely independent of the master secret, maintaining per-entity cryptographic isolation
- **Improved Documentation**: Updated multiple documents to clarify the two-tier encryption model (@HKDFId vs @Id)
- **Configuration Flexibility**: Master secret is optional if you only use @HKDFId entities

#### Security Notes

- **@HKDFId entities** (user accounts, sensitive data): Still use per-entity secrets with complete cryptographic isolation - **recommended for maximum security**
- **@Id entities** (system config, shared data): Can now use master secret for encryption - **convenient but with shared security boundary**
- **Best Practice**: Use @HKDFId for user data requiring isolation, @Id with master secret for system-level configuration

#### Breaking Changes

None - this release is fully backward compatible. Existing @HKDFId entities continue to work exactly as before.

#### Migration Guide

If upgrading from < v1.0.4 with existing @Id entities that reference @HKDFId entities:
- ✅ **Automatic**: The framework handles migration transparently during entity loading
- ✅ **No action required**: Simply upgrade and the framework will convert old references to the new secure format

See [HKDFID_VS_ID.md](docs/HKDFID_VS_ID.md) for complete details on choosing between @HKDFId and @Id entities.

---

## [1.0.5] - 2026-01-18

### 🔒 Security

#### Removed Cross-Reference Leak Vectors
- **Removed `createdByIP` field** from `Encryptable` base class
- **Removed `createdAt` field** from `Encryptable` base class

**Rationale:**
These fields represented potential cross-reference attack vectors that could leak correlation information between entities:
- `createdByIP` could reveal that "entity A was created by the same person as entity B" by correlating IP addresses
- `createdAt` could reveal temporal relationships, exposing that "entities X, Y, Z were created at similar times"

**Migration:**
If your application requires these fields, you can restore the exact same behavior by adding them to your entity classes. 

**Bonus:** Now that these fields are opt-in, you can encrypt them with `@Encrypt` for additional security:

```kotlin
class MyEntity : Encryptable<MyEntity>() {
    @HKDFId override var id: CID? = null
    
    // Restore previous default behavior (plaintext, like before)
    var createdByIP: String = EncryptableContext.getRequestIP()
    var createdAt: Instant = Instant.now()
    
    // OR encrypt them for additional security (NEW capability!)
    @Encrypt var createdByIP: String = EncryptableContext.getRequestIP()
    @Encrypt var createdAt: Instant = Instant.now()
}
```

**Benefits:**
- **Opt-in rather than mandatory** - Only add these fields if your application needs them
- **Can now be encrypted** - Previously stored in plaintext, now you can use `@Encrypt` for additional security
- **Seamless migration** - Restore exact same automatic behavior if needed

**Breaking Change:** While this is technically a breaking change if you were relying on these default fields, migration is seamless and actually provides a security upgrade - you can now encrypt these fields whereas they were always plaintext before. This update enhances cryptographic isolation by making these potentially leaky fields opt-in rather than mandatory.

---

## [1.0.6] - 2026-01-24

### ⚡ Performance

#### Code Optimizations
- **Internal refactoring** - Improved code structure and efficiency across core components
- **Memory management improvements** - Enhanced memory usage patterns for better resource utilization
- **Reduced allocations** - Optimized hot paths to minimize object creation overhead

#### Bulk Operations Support
- **Bulk entity updates** - New support for efficiently updating multiple entities in a single operation
- **Improved throughput** - Significant performance gains for applications processing large datasets

**Benefits:**
- **Better scalability** - Handle higher entity volumes with improved performance
- **Lower latency** - Reduced overhead for multi-entity operations

### 🔄 Dependencies

- **Updated Spring Boot** - Upgraded from 4.0.0 to 4.0.2

**Note:** This is a performance-focused release with no breaking changes or API modifications.

---

## [1.0.7] - 2026-01-30

### Polymorphic Relationships Support

Added support for polymorphic relationships between entities, allowing a single field to reference multiple entity types.  
Without annotations or configurations, the framework can automatically handle polymorphic associations.

---

## [1.0.8] - 2026-02-27

### 🛠️ Major Improvements & Critical Security Fixes

- **Critical Security Fix:**
  - Fixed encryption of `ByteArray` fields for `@Id` entities. Previously, the secret used for encryption was the same as the entity id, which would compromise security. Now, the correct secret (master secret) is always used for encryption in @Id entities.
  - See [MISSED_CALLSITE_BUG_1_0_8.md](docs/MISSED_CALLSITE_BUG_1_0_8.md) for a full post-mortem: what happened, why it happens to everyone, and what developers should learn from it.
- **Data Migration Support:**
  - Added robust migration logic to update existing data to the new, secure encryption scheme. Automatically detects and re-encrypts affected fields with the correct secret.
- **Storage Abstraction:**
  - Refactored storage logic to support multiple backends (e.g., GridFS, S3, inline storage). Storage classes now follow SOLID principles and are decoupled from encryption logic.
- **Documentation Updates:**
  - Updated all documentation and class/method references to use generic storage terminology instead of GridFS-specific names, preparing for S3 and other storage integrations.
- **Configuration Enhancements:**
  - Added `encryptable.migration` configuration property to control migration behavior.
  - Improved documentation for configuration and migration options.
- **Performance & Reliability:**
  - Optimized migration and storage operations for large datasets.
  - Improved error handling and logging during migration and storage operations.
- **Console Formatting:**
  - Enhanced console output with improved formatting and highlighting for better readability and user experience.
- **Testing:**
  - Expanded and revised test coverage for new storage and migration logic.
- **Spring Boot Update:**
  - Upgraded Spring Boot from 4.0.2 to 4.0.3 for improved stability and compatibility.

**Migration Notice:**
If you are upgrading from a previous version, you **MUST** start your application with `encryptable.migration=true` on the first run after upgrading to 1.0.8. This ensures all data is migrated to the new encryption scheme and prevents data access issues.

**⚠️ Important:** Do not close or interrupt the application while migration is in progress. Interrupting the migration process can result in partial updates and a **VERY HIGH RISK OF DATA CORRUPTION**. Wait until the application logs confirm that migration has completed successfully before stopping or restarting the application.

**Property Rename Required:**
After migration, you must rename the property `encryptable.gridfs.threshold` to `encryptable.storage.threshold` in all configuration files and environments. This change is necessary because Encryptable now supports multiple storage backends, not just GridFS. Failing to update this property will result in the threshold setting being ignored.

**Example:**
- Before:
  ```properties
  encryptable.gridfs.threshold=2048
  ```
- After:
  ```properties
  encryptable.storage.threshold=2048
  ```

**Note:** This release includes significant internal refactoring and security improvements. All users are strongly encouraged to upgrade and run the migration to ensure data is encrypted with the correct secret.

---

## [1.0.9] - 2026-03-07

### 🔧 Minor Fix, Major Test Expansion

#### Fix: Nested Entity IDs Incorrectly Encrypted on `@Id` Parents

- **Fixed:** The write-path population block for `encryptableListFields` was missing from `processFields` in 1.0.0–1.0.8. In practice this was a **non-issue** — `EncryptableList` already implemented the correct `isolated` guard on every mutation path (`add`, `addAll`, `removeAt`), so `encryptableListFieldMap` was always populated with the correct value (child secret for `@HKDFId` parents, child ID for `@Id` parents). The fix in 1.0.9 adds the explicit write-path block to `processFields` for correctness and consistency with the single-field path. The only observable effect of the missing block was that, for `@Id` parents that also had `@Encrypt` fields, those plaintext child IDs could be re-encrypted with the master secret on the subsequent `processFields` pass — storing an encrypted ID where a plaintext ID was expected. No secrets were exposed; the only consequence was that the nested references would fail to resolve on next load.
- **Migration:** ⚠️ **Required for `@Id` entities that have both nested `Encryptable` fields and at least one `@Encrypt` field.** In 1.0.0–1.0.8, those entities would encrypt the plaintext child IDs stored in `encryptableListFieldMap` / `encryptableFieldMap` with the master secret before persisting. The 1.0.9 migration decrypts those values back to plaintext IDs. Entities without `@Encrypt` fields, and `@HKDFId` entities, are unaffected. See the [migration guide](docs/MIGRATING_FROM_OTHER_VERSIONS.md#-migrating-to-109-from-108-or-earlier-100108) for details.

#### Key-Correctness Test Suite (`EncryptableKeyCorrectnessTest` + `EncryptableSlicedStorageTest`)

A comprehensive test suite was added to permanently close the regression gap identified after the 1.0.8 incident. These tests bypass the framework's decrypt path entirely — reading raw stored values via reflection and from storage — and assert both:
- **Correct key succeeds:** The expected key decrypts the raw ciphertext to the original plaintext.
- **Wrong key fails:** Any other key returns the encrypted input unchanged.

Codepaths covered (105 tests across 16 files):
- `String`, `ByteArray`, `List<String>` `@Encrypt` fields — `@HKDFId` and `@Id` entities
- `encryptableFieldMap` — single nested `Encryptable` fields — `@HKDFId` and `@Id` parents
- `encryptableListFieldMap` — `List<Encryptable>` fields — `@HKDFId` and `@Id` parents
- `@SimpleReference` — nested and list fields — ID stored, not secret
- `@Sliced` fields — per-slice encryption, parallel fetch, boundary correctness — `@HKDFId` and `@Id`
- `storageFields` / `storageFieldIdMap` — inline and storage-backed `ByteArray` fields

#### New Features

- **`@Sliced(sizeMB)` annotation:** Splits `ByteArray` fields into independently encrypted slices stored separately in any `IStorage` backend. Enables parallel fetch + decrypt with O(1) memory overhead per slice. Slice size 1–32 MB, default 4 MB. No `IStorage` changes required.
- **`@SimpleReference` annotation:** Marks a nested `Encryptable` field or `List<Encryptable>` field on an `@HKDFId` parent to store only the child's ID (plaintext) instead of the child's secret (encrypted). Useful for shared references where you do not own the child's secret.

---

## [1.1.0] - 2026-03-19

> **Why is this not 2.0.0?**
> The HKDF context key derivation change below is a cryptographic breaking change — it invalidates
> all previously derived keys, CIDs, and encrypted values. Under strict Semantic Versioning this
> warrants a major version bump. However, Encryptable has no known production deployments at this
> time, so absorbing the break into 1.1.0 was the pragmatic choice. See the
> [migration guide](docs/MIGRATING_FROM_OTHER_VERSIONS.md#migrating-to-110-from-109-or-earlier)
> for full rationale.

### ⚠️ Breaking Changes

#### BSON Binary Subtype: `0x04` → Custom Subtype `128`

- **Changed:** The BSON Binary subtype used to persist `CID` values has been changed from `0x04` (UUID) to custom subtype `128` (user-defined).
- **Rationale:** 
  - **Why not 0x03?** Subtype `0x04` causes MongoDB Compass and other tooling to render the bytes as hexadecimal, which is painful to read during debugging. While standard subtype `0x03` (generic binary) is displayed as Base64, using it would be semantically incorrect—CID is not a standard UUID, it's a custom cryptographic identifier. 
  - **Why custom subtype 128?** MongoDB explicitly reserves subtypes 128+ for user-defined types. Using subtype 128 correctly signals "this is a custom format" to any tool or driver reading the data, while still being displayed as Base64 in MongoDB Compass (the same rendering as 0x03, but with correct semantics). This allows future tools to distinguish CID data from standard UUID data if needed.
- **Migration:** ⛔ **No migration path.** A mechanical subtype re-encoding would be possible in isolation, but because the HKDF context key derivation also changed in this release, all derived keys and CIDs are already invalidated regardless of the subtype. Providing a partial migration that fixes the subtype while leaving the key derivation broken would be misleading. A fresh database is required.

#### HKDF Context Key Derivation: `source.toString()` → `source.name`

- **Changed:** The class name used as HKDF context during key derivation has been corrected from `source.toString()` (which produced `"class java.lang.String"`) to `source.name` (which produces `"java.lang.String"`). This is a **breaking change** — all previously derived keys (CIDs, secrets, encrypted values) are incompatible with keys derived under the new scheme.
- **Rationale:** `toString()` on a `Class` object produces an implementation-defined string with a `"class "` prefix; `name` is the canonical, stable, and deterministic class name. The old form was technically incorrect and fragile. Since no production deployments were known at the time of this change, it was the right moment to fix the foundation cleanly.
- **Migration:** ⛔ **There is no migration path.** Version 1.1.0 is fully incompatible with all prior versions. Existing data cannot be read by 1.1.0. The only path forward is a fresh database. See the [migration guide](docs/MIGRATING_FROM_OTHER_VERSIONS.md#-migrating-to-110-from-109-or-earlier) for details.

### Changes

#### Storage Threshold Minimum Lowered to 1KB

- **Changed:** The minimum configurable storage threshold has been lowered from 16KB (16384 bytes) to 1KB (1024 bytes). The default remains 16KB. Applications using a cost-efficient external storage backend (S3, R2, etc.) can now explicitly set `encryptable.storage.threshold=1024` to route any `ByteArray` field larger than 1KB to external storage.
- **Rationale:** At scale, database storage is orders of magnitude more expensive per GB than object storage. Routing binary fields to cheap object storage from 1KB onwards keeps database costs flat regardless of entity volume. The default remains 16KB as a safe, performant choice for most applications — particularly those using GridFS, which offers no cost benefit from a lower threshold.
- **Impact:** No impact unless you explicitly configure the threshold below 16KB.

#### CID Rendering Format Configuration: `encryptable.cid.base64`

- **Added:** New configuration property `encryptable.cid.base64` (default: `true`) controls how `CID.toString()` renders CID values.
  - `true` (default): CIDs render as **standard Base64 with padding** — matching exactly what MongoDB Compass displays for BSON Binary custom subtype `128` fields. Copy/paste a CID from logs directly into Compass.
  - `false`: CIDs render as **URL-safe Base64 without padding** (22 characters) — the native CID format, suitable for URLs, QR codes, and external APIs.
- **Rationale:** MongoDB Compass displays `0x03` binary data as standard Base64 with `=` padding. For developer convenience during debugging, the default aligns string representations between logs and the database tool. Applications exposing CIDs externally can opt into the compact URL-safe format.
- **String.cid Extension Enhanced:** The `String.cid` extension now accepts 4 input formats for maximum flexibility:
  - 22 characters: URL-safe Base64 (native format)
  - 24 characters: Standard Base64 with padding (MongoDB Compass format)
  - 32 characters: UUID hex without hyphens
  - 36 characters: Standard UUID format with hyphens
  - All formats are transparently converted to CID, enabling seamless round-tripping with `CID.toString()` output.
- **Impact:** Purely opt-in configuration. Default behavior provides better developer experience with Compass.

---


## [1.2.0] - 2026-04-02

### ⚠️ Data Format Change: @Sliced Reference Header (4-byte → 8-byte Size Prefix)

- **Changed:** The internal reference header format for `@Sliced` `ByteArray` fields has been updated. The size prefix in the reference header is now stored as an 8-byte `Long` (previously a 4-byte `Int`).
- **Why:** This change was needed to support theoretical file sizes up to 3 petabytes (PB), far beyond the previous 2 gigabyte (GB) limit imposed by a 4-byte `Int` (Int.MAX_VALUE = 2,147,483,647 bytes). While most applications will never approach these limits, the new format ensures Encryptable remains future-proof and consistent with storage best practices.
- **Migration:** Existing data using the old 4-byte size prefix must be migrated to the new format. A migration utility is provided to automatically update all affected documents in MongoDB. See the [migration guide](docs/MIGRATING_FROM_OTHER_VERSIONS.md#migrating-to-120-from-110) for instructions. New data will always use the new 8-byte format.
 - **Impact:** Applications reading `@Sliced` fields from databases created with Encryptable 1.1.0 or earlier must run the migration before upgrading to 1.2.0. Failure to migrate will result in the application being completely unable to load or delete affected fields: the new code expects an 8-byte header and will not recognize or process old references with a 4-byte header at all.


### 🔐 Enhanced Security: Master Secret & @HKDFId Validation

This release significantly strengthens cryptographic entropy enforcement for both master secrets and @HKDFId entity secrets, providing a stronger security margin and improved audit posture.

- **Enhanced:** Master secret validation now enforces two requirements:
  1. **Minimum length:** At least 74 characters to guarantee 256 bits of entropy (at 3.5 bits per character minimum)
  2. **Entropy validation:** Uses Shannon entropy analysis (same as `String.randomSecret()`) to ensure the secret has sufficient randomness
- **Why:** Length alone does not guarantee security — a long predictable string is weaker than a truly random secret. Entropy validation ensures the master secret has sufficient cryptographic strength. The 74-character minimum with entropy ≥3.5 bits/char provides approximately 259 bits of entropy, exceeding the 256 bits required for AES-256 key derivation via HKDF-SHA256.
- **Error Messages:**
  - Length violation: `"Master Secret must be at least 74 characters to guarantee 256-bit entropy (got X characters)."`
  - Entropy violation: `"Master Secret has insufficient entropy"`
- **Audit Logging:** The framework now logs a warning when an attempt is made to update an already-set master secret, and logs an info message when the master secret is successfully set. No secret material is ever logged.
- **Dynamic Update:** The master secret can now be updated at runtime. Changing the master secret does NOT automatically re-encrypt existing data — a secret rotation is required for that.
- **Impact:** Any application setting the master secret programmatically must ensure the secret is both long enough AND high-entropy. Use `String.randomSecret()` or similar cryptographically-secure random generators. Environment variables and configuration files should be validated before deployment.

### 🔐 @HKDFId Secret Length Increased (32 → 48 characters)

- **Changed:** Minimum secret length for `@HKDFId` entities increased from 32 to 48 characters.
- **Why:** A 48-character Base64 URL-safe secret (generated with `SecureRandom`) represents 36 bytes = 288 bits of entropy, exceeding the 256-bit AES-256 key requirement without relying on HKDF expansion. This provides a stronger security margin and aligns with the same entropy philosophy applied to the master secret.
- **`String.randomSecret()` updated:** Default byte length changed from 32 bytes (43 chars) to 36 bytes (48 chars) to match the new minimum.
- **Impact:** Applications that manually create secrets for `@HKDFId` entities must now provide at least 48 characters. Applications using `String.randomSecret()` are automatically compliant.

⚠️ **Breaking Change Warning:**
- **For applications previously using the 32-character minimum:** This is a breaking change. The framework will now reject any secret shorter than 48 characters for `@HKDFId` entities, throwing an `IllegalArgumentException` with message `"For HKDFID strategy, the secret must be at least 48 characters long."`
- **Migration required:** If your application was using exactly 32-character secrets, you must update them to at least 48 characters before upgrading to 1.2.0.
- **Recommended action:** Use `String.randomSecret()` to generate new secrets with the correct length and entropy. If manually constructing secrets, ensure they are at least 48 characters AND have sufficient entropy (Shannon entropy ≥3.5 bits/character, validated via `SecurityUtils.hasMinimumEntropy()`).
- **Impact:** Any application setting the master secret programmatically must ensure the secret is both long enough AND high-entropy. Use `String.randomSecret()` or similar cryptographically-secure random generators. Environment variables and configuration files should be validated before deployment.

### 🛠️ Dependency Update

- **Spring Boot:** Upgraded from 4.0.3 to 4.0.5.

### ⚡ Performance Improvement

- **Improved encryption/decryption efficiency** by removing an intermediate array in the cryptographic operations. This reduces memory allocations and speeds up both encryption and decryption, especially for large or frequent operations.
- **Enhanced reflection performance:** `Method.unreflect()` now returns a `MethodHandle` instead of the original `Method` object, leveraging near-native method access performance. Using `MethodHandle` provides significantly better performance compared to `Method.invoke()` reflection. This is especially important for frequently-called internal framework operations that use reflection to access private methods (like `throwIfReadOnly()`). Aspects and internal framework code that rely on reflected method calls now benefit from MethodHandle's near-native performance characteristics, reducing reflection overhead.

### 🧹 Improved Cascade Delete Logic

- **Improved cascade delete logic:** Cascade deletion is now more robust and reliable, ensuring all referenced child entities and storage resources are properly cleaned up, even in complex nested scenarios.

### 📚 Improved Documentation

- **Enhanced documentation** across cryptographic and security-critical components to provide clearer explanations of design decisions, entropy requirements, and performance considerations.

### 🗂️ Package Reorganization

- **Moved to core package:** `CID.kt`, `EncryptableInterceptor`, and `EncryptableRunner` have been moved to the `tech.wanion.encryptable` package for better organization and to reflect their role as core framework components.
- **Impact:** Update import statements to use the new package location.

### 🔒 New Feature: Read-Only Entity Protection

- **Added:** New `throwIfReadOnly()` mechanism that prevents modification of entities after they have been saved.
- **What it protects:** Mirror fields (nested entities, lists, storage references) are protected from dangerous modifications on saved entities.
- **Why it matters:** After an entity is saved, the framework doesn't expect it to be changed right after, therefore it is not being tracked for changes. Without this prevention mechanism, allowing modifications to mirror fields would cause data corruption:
  1. The aspect detects the modification (setting a nested entity to null, clearing lists, etc.)
  2. It deletes the referenced data (nested entity, list items, or storage files)
  3. But the parent entity itself is not updated (not being tracked)
  4. The parent is left in an inconsistent state with orphaned references
- **Why this is subtle:** Most developers wouldn't naturally think about this scenario because:
  - Normal ORMs don't have this problem (they use lazy proxies or session-managed entities)
  - Encryptable's aspect-based approach means modifications are *always* intercepted, even on detached entities
  - The delete happens immediately (via aspect), but the parent entity isn't aware it's no longer tracked
  - The corruption only manifests on the *next* load, making it difficult to diagnose
- **Behavior:** Throws `IllegalStateException` when attempting to modify mirror fields on saved entities, preventing the corruption scenario entirely.
- **Comprehensive Documentation:** Added detailed KDoc explaining the protection mechanism, when it triggers, and the data corruption scenarios it prevents.

### 🧪 Enhanced Test Coverage: Read-Only Entity Protection

- **Added comprehensive test suite** (`EncryptableAfterSaveTest`) with 6 tests covering all scenarios where entities must be protected from modification after save:
  - Setting single nested entity fields to null
  - Clearing lists of nested entities
  - Setting storage-backed ByteArray fields to null
  - Null assignments after session end (detached entities)
  - Multiple nested fields on the same entity
  - Adding new items to lists on detached entities
- **Test-Driven Development:** Tests were created first and initially failed before the `throwIfReadOnly()` protection mechanism was implemented. The feature was built to make all tests pass.
- **Verification approach:** Each test not only verifies the exception is thrown, but also confirms that the child entities/data were NOT actually deleted, proving the protection mechanism works correctly.
- **Regression prevention:** These tests provide comprehensive coverage of the exact scenarios that could cause data corruption if mirror field protection failed.

### 🔄 New Feature: Master Secret Rotation

- **Added:** `MasterSecretHolder.rotateMasterSecret(oldMasterSecret, newMasterSecret)` method for rotating the master secret system-wide.
- **⚠️ Important:** Master secret rotation is **NOT automatic**. The framework provides the infrastructure to perform rotation, but **you must manually write the business logic to trigger it**. This typically involves:
  - A scheduled job (cron, Quartz, etc.) that periodically initiates rotation
  - An admin endpoint that allows manual rotation triggers
  - A background service that monitors rotation policies and executes rotation when needed
  - The rotation must happen during a maintenance window when `@Id` entity writes are paused (see "Operational requirements" below)
- **What it does:** Re-encrypts all `@Encrypt` fields on all `@Id` (non-isolated) entities from the old master secret to the new master secret.
- **Scope:** 
  - Re-encrypts `@Encrypt` String, List<String>, nested object, and ByteArray fields
  - Handles inline ByteArray fields (stored directly in documents)
  - Handles storage-backed ByteArray fields (stored in GridFS, S3, or other backends)
  - Handles `@Sliced` ByteArray fields with independent encryption per slice
  - Recursively re-encrypts inner `@Encrypt` fields of nested objects
  - Completely skips `@HKDFId` (isolated) entities — they use per-entity secrets and are unaffected
- **Safety guarantees:**
  - **Idempotent retry-safe:** Uses identity checks to detect already-rotated documents. If rotation fails partway, retry safely skips documents that were already processed.
  - **Atomic replace pattern:** For storage-backed fields, new data is created before old data is deleted, ensuring no data loss if operation fails.
  - **All-or-nothing semantics:** Master secret is only updated after all documents across all repositories are successfully rotated. If any repository fails, the operation throws an exception and the secret is NOT updated, allowing retry.
  - **Memory hygiene:** Old and new secrets are marked for wiping at request end.
  - **Comprehensive logging:** Audit trail without exposing secret material (only logs metadata: entity type, document ID, timestamp, status).
- **Operational requirements:**
  - **Maintenance window:** Perform during a maintenance window when `@Id` entity writes are paused. Concurrent writes could result in data encrypted with either old or new secret.
  - **Storage considerations:** For very large datasets, storage-backed fields temporarily consume double storage (new slices exist before old ones are deleted).
  - **Performance:** Rotation speed depends on total number of `@Id` documents with `@Encrypt` fields. Uses parallel repository iteration for throughput.
- **Usage:**
  ```kotlin
  val oldSecret = MasterSecretHolder.getMasterSecret()
  val newSecret = "new-master-secret-at-least-74-chars-with-high-entropy..."
  MasterSecretHolder.rotateMasterSecret(oldSecret, newSecret)
  // On success, the new master secret is active; new @Id entities will use it.
  ```

---

## [1.2.1] - 2026-04-07

### 🔄 Patch Release: Stability & Documentation

This patch release maintains full backward compatibility with 1.2.0 while introducing stability improvements and enhanced documentation.

#### ✅ No Breaking Changes

- **Fully backward compatible** with 1.2.0 — no data format changes, no API modifications
- **No migration required** — simply upgrade and continue using Encryptable as before
- **Drop-in replacement** for 1.2.0 dependencies

#### 🔧 Bug Fixes

- **Fixed GridFS-backed field cascade deletion** - Previously a boundary condition in cascade delete logic (changed `<=` to `<`) was preventing GridFS-backed `ByteArray` fields from being properly deleted during cascade operations on parent entities.
- **Improved cascade delete ordering** - Entity deletion now occurs **only after cascade delete completes**. This ensures all child entities, nested references, and storage resources are properly cleaned up before the parent entity is removed from the database, preventing orphaned data and maintaining referential integrity.

#### ⚡ Performance Improvements

- **Optimized @Sliced annotation validation** - Moved validation of `@Sliced` annotations on `ByteArray` fields from every read/write operation to encryptable metadata initialization. This eliminates repeated validation overhead and improves performance for entities with sliced storage fields, especially for frequently accessed or large batch operations.
- **Batch deletion of storage-backed fields** - When cascade-deleting multiple entities (e.g., `deleteBySecrets`), storage references (GridFS ObjectIds, S3 CIDs, etc.) are now collected across all entities in parallel and issued as a single `deleteMany` call per field, rather than one delete call per entity per field. For `@Sliced` fields, all individual slice references across all entities are batched into a single bulk delete. This reduces the number of round-trips to the storage backend from O(entities × fields) to O(distinct fields), providing significant performance gains on large batch deletes.
- **`GridFSStorage.deleteMany` override** - `GridFSStorage` now overrides `deleteMany` to issue a single `{ _id: { $in: [...] } }` query to MongoDB instead of one delete operation per reference. This eliminates all per-file network round-trips and provides maximum performance for batch GridFS deletions, regardless of the batch size.

#### 📋 Dependency Versions

- **Kotlin:** 2.2.21
- **Spring Boot:** 4.0.5
- **AspectJ:** 1.9.22
- **Java:** 21+

---

## [1.2.2] - 2026-04-10

### 🔒 Security Patch: Critical Rotation Fixes & Memory Hygiene

This patch release addresses two critical bugs in master secret rotation, a memory-hygiene defect in secret wiping, and an edge case in storage field cleanup.

#### ✅ No Breaking Changes

- **Fully backward compatible** with 1.2.1 — no data format changes, minimal API modifications
- **No migration required** — simply upgrade and continue using Encryptable as before
- **Drop-in replacement** for 1.2.1 dependencies

#### 🔧 API Refinement

- **Renamed:** `getReferenceBytes` → `getReference` on `ResourceHandler` for clearer naming and consistency with storage abstraction terminology.
- **Rationale:** While the method returns bytes, the naming aligns with the storage abstraction model where references are the primary concept. This improves API clarity and consistency with other storage methods like `getSlices()`, keeping the naming cleaner and more intuitive.

#### 🐛 Bug Fix: Unsaved Entity Storage Cleanup

- **Fixed:** Storage-backed `ByteArray` fields on unsaved entities are now properly deleted when an entity modification fails or is rolled back before the parent entity is saved.
- **Scenario:** If an entity with storage-backed fields is modified but never successfully saved to the database (e.g., validation fails, exception occurs before `save()` completes), the framework now correctly cleans up any storage references that were created during field assignment, preventing orphaned storage data.
- **Impact:** Eliminates potential storage leaks for applications that perform multiple entity save attempts or partial saves on the same entity instance.

#### 🔒 Security Fix: Master Secret Comparison Now Constant-Time

- **Fixed:** The `rotateMasterSecret()` validation compared the stored master secret against the caller-provided old secret using Kotlin's `==` operator, which performs a short-circuit string comparison — it returns `false` as soon as the first non-matching byte is found. This allows a timing side-channel: an attacker making repeated rotation calls with slightly different guesses could, in principle, measure response times to discover how many leading characters of the master secret are correct.
- **Impact:** Low — exploiting this in practice requires many precise timing measurements under controlled network conditions, and the attack would still need to reconstruct a 74+ character high-entropy secret. However, constant-time comparisons are a cryptographic best practice and the fix is trivial.

#### 🔒 Security Fix: Master Secret was Being Zerified After Rotation

- **Fixed:** A critical bug where the new master secret was marked for end-of-request wiping and then assigned directly to `MasterSecretHolder.masterSecret`. At HTTP request end, `EncryptableInterceptor` would zerify the `newMasterSecret` parameter — which was the same object as `masterSecret` — silently destroying the just-rotated secret and rendering all subsequent encryption operations non-functional.
- **Root cause:** `markForWiping(oldMasterSecret, newMasterSecret)` was called before `masterSecret = newMasterSecret`. Since both variables pointed to the same String object, the wipe at request end corrupted the stored secret.
- **Fix:** `masterSecret` is now assigned a `.copy()` of `newMasterSecret` before marking the original for wiping. The canonical stored value is a distinct object from the one in the wipe queue.
- **Impact:** Any application that called `rotateMasterSecret()` during an HTTP request would silently break after the request completed. This fix is critical for all users of the rotation feature.

#### 🔒 Security Fix: Storage Data Loss During Rotation on MongoDB Write Failure

- **Fixed:** During `rotateMasterSecret()`, old storage objects (GridFS/S3/custom) were deleted before the MongoDB document was updated with the new references. If `collection.replaceOne()` failed after storage deletion, the document would retain stale references pointing to now-deleted storage objects, causing permanent data loss with no recovery path.
- **Fix:** Old storage deletions are now deferred via a `pendingDeletions` list. Deletions execute only **after** `replaceOne()` succeeds. If the MongoDB write fails, old storage data remains intact and the rotation can be safely retried (the idempotent identity-check mechanism continues to apply).
- **Impact:** Rotation operations against unreliable MongoDB connections or under heavy load could have silently destroyed storage-backed encrypted fields.

#### 🔒 Security Fix: `zerify()` Could Silently Skip Live Secrets

- **Fixed:** `zerify()` had an early-return guard — `if (this.hashCode() == 0) return` — intended as an optimization to skip already-wiped strings. This guard was incorrect and dangerous: any string whose hash code happens to be zero (a ~1-in-4-billion chance for arbitrary input, but possible for any string) would be silently skipped and **left in memory unwiped**.
- **Root cause:** `hashCode() == 0` is not a reliable indicator of "already zerified". A string can have a genuine hash of 0 without its contents being null bytes. Calling `hashCode()` also computes and caches the hash as a side effect, which can mask the condition on subsequent calls.
- **Fix:** The early-return guard is removed entirely. `zerify()` now unconditionally overwrites the internal byte array. Re-zerifying an already-zerified string is safe and costs essentially nothing (filling an already-zero array).
- **Impact:** Any secret string that happened to produce a hash code of zero would never be wiped from memory, defeating the entire purpose of the wiping mechanism for that value.

#### 📚 Documentation Enhancements

- **Clarified security model:** Significantly expanded [NOT_EXACTLY_ZERO_KNOWLEDGE.md](docs/NOT_EXACTLY_ZERO_KNOWLEDGE.md) with a dedicated "Why Transient Knowledge?" section that explains the architectural tradeoff between client-side purity and server-side functionality. Added precise terminology: "zero-knowledge outside requests" vs. "transient knowledge during requests" vs. "hardware-isolated transient knowledge with memory enclaves."
- **Improved conceptual clarity:** Documentation now clearly distinguishes between the three security states and explains why transient knowledge is a deliberate design choice, not a compromise.
- **Better audience alignment:** Rewording provides clarity for security engineers, architects, and developers at all levels to understand the framework's security posture and design rationale.

#### 📋 Dependency Versions

- **Kotlin:** 2.2.21
- **Spring Boot:** 4.0.5
- **AspectJ:** 1.9.22
- **Java:** 21+

---

## Contributing

See [Contributing](CONTRIBUTING.md) for guidelines on bug reports, feature requests, and pull requests.

## Reporting Security Issues

If you discover a security vulnerability, please open a [GitHub Issue](https://github.com/WanionTechnologies/Encryptable/issues) with the "security" label or contact the maintainer directly.

