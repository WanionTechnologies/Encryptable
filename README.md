# Encryptable Framework

Encryptable is a security-first extension of Spring Data MongoDB that adds encryption, ORM-like features, and cryptographic addressing with minimal developer effort.

**What you get:**
- **Instant data access** – No username lookups or mapping tables needed.
- **Automatic encryption** – Add `@Encrypt` to supported fields* and get AES-256-GCM encryption with per-user isolation.
- **Smart polymorphism** – Use abstract types in your code, and the framework automatically preserves concrete types.
- **Real relationships** – One-to-One, One-to-Many, Many-to-Many with cascade delete (optional).
- **Automatic Change Detection & Efficient updates** – Only changed fields are sent to the database.
- **Large file handling** – Seamless integration for files up to 2GB (GridFS, S3, or custom backends). Any field can act as a mirror for large files, with Encryptable automatically handling storage, encryption, and lazy loading. Built-in backends require no code; custom backends only need to implement the `IStorage` interface.


\* **Supported field types** for encryption include: `String`, `ByteArray`, `List<String>`, and any custom type that can be serialized to JSON.

All features work through simple annotations, no boilerplate, minimal configuration.
No crypto expertise required.

```kotlin
@Document class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null           // Allows Cryptographic addressing
    @Encrypt var email: String? = null             // Automatic encryption
    @PartOf var address: Address? = null           // Cascade delete
    var payment: Payment<*>? = null                // Polymorphic—type preserved
}
```

---

## 🔑 Why Encryptable?

**Most encryption libraries protect data at rest *or* in transit — Encryptable does both, entirely at the application level. Data is encrypted before it reaches the database and before it leaves the server.**

Encryptable operates in two encryption modes, selected by annotating the entity's `id` field:

- **`@Id` entities** are encrypted with a shared **master secret** — strong protection with centralized control. The server holds this secret and can encrypt/decrypt all entities of this type.
- **`@HKDFId` entities** derive a **unique encryption key per entity** from the user's own secret via HKDF, achieving full cryptographic isolation between entities. The server never stores user secrets, meaning it cannot read any `@HKDFId` entity's data without that user's secret — even if the entire database is compromised.

With traditional encryption, your server holds the keys — a database breach still exposes everything. With `@HKDFId`, Encryptable flips this: keys are derived on-demand from user secrets, never stored, and gone after the request ends.

**Choosing an entity type:**

|                              | `@Id` Entity                                                                                              | `@HKDFId` Entity                                                                                                                                                                 |
|------------------------------|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Encryption key source**    | Shared master secret (server-held)                                                                        | Derived per-entity from the user's own secret via HKDF                                                                                                                           |
| **Server can read data**     | ✅ Yes, with the master secret                                                                             | ❌ No — user secret is never stored by the server                                                                                                                                 |
| **Cryptographic isolation**  | Shared across all `@Id` entities                                                                          | Full isolation — every entity has a unique derived key                                                                                                                           |
| **Database stolen**          | Data is safe as long as master secret is secure                                                           | Data is safe unconditionally — keys never exist on the server                                                                                                                    |
| **Cryptographic addressing** | ❌ No                                                                                                      | ✅ Yes — ID is derived from the user secret via HKDF                                                                                                                              |
| **Addressing mechanism**     | `CID` without cryptographic addressing — ID is not derived from any secret                                | Cryptographic addressing — the entity's ID (`CID`) is deterministically derived from the user's secret via HKDF, eliminating the need for username/email mapping tables entirely |
| **Secret rotation**          | `MasterSecretHolder.rotateMasterSecret()` — re-encrypts all `@Id` entities across all collections at once | Per-entity via `repository.rotateSecret()` — user-initiated; the old entity is replaced with a new one, and the ID changes (since ID is derived from the secret)                 |
| **Typical use case**         | System/internal data, shared or admin-owned records                                                       | User-owned private data requiring per-user isolation                                                                                                                             |

**Why entity type matters:** `@Id` entities can be queried by plaintext fields (email, username, etc.) *and* even by encrypted fields — since all entities share the same master secret, you can encrypt your search value with that secret, and the deterministic encryption will match (provided the HKDF context/info is consistent). However, `@Id` entities should only be used for non-critical information, as the server can decrypt all encrypted fields with the master secret.  
`@HKDFId` entities, by contrast, are indexed only by their ID; the server cannot decrypt data without the user's secret, preserving privacy even against the server operator. \**as long best practices are followed and secrets are never logged or stored.*

**Honest Security Model:** Request-scoped (transient knowledge). The server processes secrets during requests to encrypt/decrypt data, this is not **exactly** zero-knowledge.
**Full transparency:** [Security Model](docs/NOT_EXACTLY_ZERO_KNOWLEDGE.md) • [Limitations](docs/LIMITATIONS.md)

**Learn More:** [Technical Innovations](docs/INNOVATIONS.md) • [AI Security Audit](docs/AI_SECURITY_AUDIT.md) • [Best Practices](docs/BEST_PRACTICES.md) • [Changelog](CHANGELOG.md)

---

## 📦 Installation

Add Encryptable to your `build.gradle.kts`:

```kotlin
plugins {
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.freefair.aspectj.post-compile-weaving") version "9.0.0"
}

dependencies {
    // Encryptable Starter (includes all required dependencies)
    implementation("tech.wanion:encryptable-starter:1.2.2")
  
    // Encryptable Aspects
    aspect("tech.wanion:encryptable:1.2.2")
}
```

For a full installation guide, including MongoDB setup and Spring configuration, see [Prerequisites](docs/PREREQUISITES.md).

---

## 🆘 Getting Help

- **Have questions?** Check the [FAQ](docs/FAQ.md) for common questions and answers
- **Need support?** Open a [GitHub Issue](https://github.com/WanionTechnologies/Encryptable/issues)
- **Found a bug?** Report it on [GitHub Issues](https://github.com/WanionTechnologies/Encryptable/issues)

---

## 🤗 Community & Contributions
- [How to Contribute](CONTRIBUTING.md): Guidelines for bug reports, feature requests, pull requests, and coding standards.
- [Code of Conduct](CODE_OF_CONDUCT.md): Our commitment to a welcoming, inclusive, and respectful community.

---

## 📖 Documentation

- [Project Documentation](docs/README.md)
- [Usage Examples](examples/README.md)

---

## ✅ Test Runtime
Test Runtime ✅ 112 passing tests (100%) | 0 failing  

Detailed test overview [here](src/test/kotlin/cards/project/README.md).

---

## 🙏 Sponsors

This project is supported by these amazing sponsors:

### 🏢 Company Sponsors

[**Be the First!**](https://github.com/sponsors/WanionCane)

### 👤 Individual Sponsors

[**Be the First!**](https://github.com/sponsors/WanionCane)

---

## 👤 About the Author

Encryptable was created by WanionCane, an independent developer with a passion for privacy, security, and innovative software design.\
Prior to Encryptable, WanionCane authored several popular Minecraft mods, including UnIDict, Avaritiaddons, and Bigger Crafting Tables, which together have amassed over 100 million downloads on CurseForge.\
This project was built from the ground up, with AI assistance for documentation and repetitive tasks, to implement request-scoped (transient) knowledge architecture and ORM-like features to the Java and Kotlin ecosystem.

---

## ⚖️ Responsible Use & Ethics

Encryptable is designed to empower privacy, security, and data protection for all users.  
However, as any privacy technology, it can be misused.  
**The author and maintainers of Encryptable do not condone, support, or agree with any harmful, illegal, or unethical usage of this project.  
Users are solely responsible for ensuring their usage of Encryptable complies with all applicable laws and ethical standards.**

*This disclaimer is standard for all privacy technologies and does not imply any unique risk or concern with Encryptable specifically.*

---

## 📝 License
This project is licensed under the MIT License. See [License](LICENCE) for details.