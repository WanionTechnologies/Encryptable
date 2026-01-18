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

---
## [1.0.0] - 2025-12-12 (Initial Release)

### 🎉 Initial Release

Encryptable 1.0.0 marks the first release of the framework, providing production-ready request-scoped (transient) knowledge encryption and ORM-like features for MongoDB.

> **Why is Encryptable a framework and not just a library?** See [FAQ: Why is Encryptable called a "Framework"?](docs/FAQ.md#-why-is-encryptable-called-a-framework-instead-of-a-library)

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

## Contributing

See [Contributing](CONTRIBUTING.md) for guidelines on bug reports, feature requests, and pull requests.

## Reporting Security Issues

If you discover a security vulnerability, please open a [GitHub Issue](https://github.com/WanionTechnologies/Encryptable/issues) with the "security" label or contact the maintainer directly.
