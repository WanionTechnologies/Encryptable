# Encryptable Framework

Encryptable is a security-first extension of Spring Data MongoDB that adds encryption, ORM-like features, and cryptographic addressing with minimal developer effort.

**What you get:**
- **Instant data access** – No username lookups or mapping tables needed.
- **Automatic encryption** – Add `@Encrypt` to any field and get AES-256-GCM encryption with per-user isolation.
- **Smart polymorphism** – Use abstract types in your code, and the framework automatically preserves concrete types.
- **Real relationships** – One-to-One, One-to-Many, Many-to-Many with cascade delete (optional).
- **Automatic Change Detection & Efficient updates** – Only changed fields are sent to the database.
- **Large file handling** – Store files up to 2GB without managing file I/O. Any field can act as a mirror for large files, with Encryptable automatically handling storage (using any pluggable backend—GridFS, S3, file system, etc.), encryption, and lazy loading. No backend-specific code required.

All features work through simple annotations—no boilerplate, minimal configuration.
No crypto expertise required.

```kotlin
@Document class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null           // Allows Cryptographic addressing
    @Encrypt var email: String? = null             // Automatic encryption
    @PartOf var address: Address? = null           // Cascade delete
    var payment: Payment<*>? = null                // Polymorphic—type preserved
}
```

**Honest Security Model:** Request-scoped (transient knowledge). The server processes secrets during requests to encrypt/decrypt data, this is not **EXACTLY** zero-knowledge.
**Full transparency:** [Security Model](docs/NOT_EXACTLY_ZERO_KNOWLEDGE.md) • [Limitations](docs/LIMITATIONS.md)

**Learn More:** [Technical Innovations](docs/INNOVATIONS.md) • [AI Security Audit](docs/AI_SECURITY_AUDIT.md) • [Changelog](CHANGELOG.md)

---

## ⚡ Quick Start

### 📦 Installation

For a full installation guide, see [Prerequisites](docs/PREREQUISITES.md).

### 🛠️ Basic Usage

```kotlin
@EnableEncryptable
@SpringBootApplication
class Application

// All entities must extend Encryptable<T>
@Document class User : Encryptable<User>() {
    // HKDFId: derives CID from secret using HKDF
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
}

@Document class Device : Encryptable<Device>() {
    // @Id: uses the 22-character Base64 URL-Safe String directly, making it a non-secret.
    @Id override var id: CID? = null
    // for entities with @Id, now you can use @Encrypt.
    // the master secret needs to be configured.
    // which means, this entity will be encrypted using a shared secret.
    @Encrypt var serial: String? = null
}

// All repositories must extend EncryptableMongoRepository<T>
interface UserRepository : EncryptableMongoRepository<User>
interface DeviceRepository : EncryptableMongoRepository<Device>
```

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
Prior to Encryptable, WanionCane authored several popular Minecraft mods—including UnIDict, Avaritiaddons, and Bigger Crafting Tables—which together have amassed over 100 million downloads on CurseForge.\
This project was built from the ground up, with AI assistance for documentation and repetitive tasks, to implement request-scoped (transient) knowledge architecture and ORM-like features to the Java and Kotlin ecosystem.

---

## 🤝 Sponsor the author
If you find Encryptable useful, consider sponsoring the author to help fund maintenance, documentation, and professional security audits.

**Why sponsor?** Professional cryptographic audits ($4-6k) enable enterprise adoption and benefit the entire open-source community. Your support helps make strong privacy and request-scoped (transient) knowledge security accessible to everyone.

- [Sponsor on GitHub](https://github.com/sponsors/WanionCane)
- Or click the "Sponsor" button in this repository.

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