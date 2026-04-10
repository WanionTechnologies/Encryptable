# Encryptable Framework

Encryptable is a security-first extension of Spring Data MongoDB that adds encryption, ORM-like features, and cryptographic addressing with minimal developer effort.

**What you get:**
- **Instant data access** – No username lookups or mapping tables needed.
- **Automatic encryption** – Add `@Encrypt` to any field and get AES-256-GCM encryption with per-user isolation.
- **Smart polymorphism** – Use abstract types in your code, and the framework automatically preserves concrete types.
- **Real relationships** – One-to-One, One-to-Many, Many-to-Many with cascade delete (optional).
- **Automatic Change Detection & Efficient updates** – Only changed fields are sent to the database.
- **Large file handling** – Seamless integration for files up to 2GB (GridFS, S3, or custom backends). Any field can act as a mirror for large files, with Encryptable automatically handling storage, encryption, and lazy loading. Built-in backends require no code; custom backends only need to implement the `IStorage` interface.

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

**Most encryption libraries protect data at rest. Encryptable makes unauthorized access impossible, not even the server can read user data without the user's secret.**

With traditional encryption, your server holds the keys, a database breach still exposes everything. Encryptable flips this: each user's data is encrypted with keys derived from *their* secret, which the server never stores. After the request ends, the key is gone.

|                          | Traditional Encryption                            | Encryptable Framework                                  |
|--------------------------|---------------------------------------------------|--------------------------------------------------------|
| **Database stolen**      | Attacker has encrypted data + keys on same server | Attacker has encrypted data, keys don't exist anywhere |
| **Server compromised**   | All users exposed                                 | Only active sessions at risk                           |
| **Username→data lookup** | Requires mapping tables                           | Cryptographic addressing                               |
| **Developer effort**     | Manual key management, rotation, per-field logic  | `@Encrypt` annotation, done                            |

**The core innovation, Cryptographic Addressing:** Your user's ID is derived from their secret via HKDF, the server never stores the secret.  
Because there's no username/email mapping table needed for lookups, there's nothing to leak, nothing to correlate.  
The server can list documents, but cannot identify or correlate them to specific users without their secrets — *as long as best practices are followed*.

**Honest Security Model:** Request-scoped (transient knowledge). The server processes secrets during requests to encrypt/decrypt data, this is not **exactly** zero-knowledge.
**Full transparency:** [Security Model](docs/NOT_EXACTLY_ZERO_KNOWLEDGE.md) • [Limitations](docs/LIMITATIONS.md)

**Learn More:** [Technical Innovations](docs/INNOVATIONS.md) • [AI Security Audit](docs/AI_SECURITY_AUDIT.md) • [Best Practices](docs/BEST_PRACTICES.md) • [Changelog](CHANGELOG.md)

---

## 📦 Installation

For a full installation guide, see [Prerequisites](docs/PREREQUISITES.md).

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