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
- Added a new document [Not Zero-Knowledge](docs/NOT_ZERO_KNOWLEDGE.md) for full explanation and apology.
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

## Contributing

See [Contributing](CONTRIBUTING.md) for guidelines on bug reports, feature requests, and pull requests.

## Reporting Security Issues

If you discover a security vulnerability, please open a [GitHub Issue](https://github.com/WanionTechnologies/Encryptable/issues) with the "security" label or contact the maintainer directly.
