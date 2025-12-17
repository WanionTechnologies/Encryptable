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

---

## [1.0.0] - 2025-12-12 (Initial Release)

### 🎉 Initial Release

Encryptable 1.0.0 marks the first release of the framework, providing production-ready zero-knowledge encryption and ORM-like features for MongoDB.

> **Why is Encryptable a framework and not just a library?** See [FAQ: Why is Encryptable called a "Framework"?](docs/FAQ.md#-why-is-encryptable-called-a-framework-instead-of-a-library)

### Core Features

#### 🔐 Zero-Knowledge Cryptographic Architecture
- **AES-256-GCM encryption** for field-level data protection
- **HKDF-based deterministic CID generation** (RFC 5869)
- **Per-user cryptographic isolation** - each user's data encrypted with their own derived keys
- **Cryptographic addressing for database ORM** - stateless, zero-knowledge data access without username→ID mapping tables (novel application of content-addressable principles to relational data modeling)
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

## [1.0.2] - 2025-12-17

### Aspect Application Reliability

- Turns out the changes in 1.0.1 were not enough to guarantee reliable aspect application.
- Improved documentation around Gradle setup to ensure aspects are always applied correctly.
- If you were affected by aspect application issues in 1.0.0 or 1.0.1, please follow the updated Gradle configuration instructions in [Prerequisites](docs/PREREQUISITES.md) to resolve the issue.

> **Help needed**: If you know a better way to ensure aspects are always applied correctly transparently to the user, please open a PR or issue to discuss!

--

## Contributing

See [Contributing](CONTRIBUTING.md) for guidelines on bug reports, feature requests, and pull requests.

## Reporting Security Issues

If you discover a security vulnerability, please open a [GitHub Issue](https://github.com/WanionTechnologies/Encryptable/issues) with the "security" label or contact the maintainer directly.
