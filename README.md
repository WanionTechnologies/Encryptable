# Encryptable Framework

Encryptable is an ORM-like, Zero-Knowledge Data Management Framework for Spring Data MongoDB.

Enabling Direct Lookup `O(1)` of entities via Cryptographic Addressing.

Supporting AES-256-GCM field-level encryption, per-user cryptographic isolation, and intelligent relationship management.

---

**Main Innovations:**
- Cryptographic addressing (zero mapping tables)
- ORM-like relationships in MongoDB
- Anonymous zero-knowledge architecture
- Intelligent change detection
- And more! Check [Innovations](docs/INNOVATIONS.md) for the full list.

See [AI Security Analysis](docs/AI_SECURITY_AUDIT.md) for detailed cryptographic review and [Limitations](docs/LIMITATIONS.md) for important technical constraints.

For version history and release notes, see [Changelog](CHANGELOG.md).

---

## 🛠️ Scope and Application-Level Features

Encryptable is intentionally focused on providing secure, innovative data management and encryption for MongoDB—including cryptographic addressing, zero-knowledge security, field-level encryption, and ORM-like relationships.\
Other advanced features such as multi-tenancy or external KMS integration are left for the developer to implement as needed.\
This design keeps Encryptable simple, flexible, and easy to integrate into a wide variety of projects.

---

## ⚡ Quick Start
```kotlin
@EnableEncryptable
class Application

// All entities must extend Encryptable<T>
class User : Encryptable<User>() {
    // HKDFId: derives CID from secret using HKDF
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
}

class Device : Encryptable<Device>() {
    // @Id: uses the 22-character Base64 URL-Safe String directly, making it a non-secret.
    @Id override var id: CID? = null
    // for entities with @Id, you cannot use @Encrypt.
    var serial: String? = null
}

// All repositories must extend EncryptableMongoRepository<T>
interface UserRepository : EncryptableMongoRepository<User>
interface DeviceRepository : EncryptableMongoRepository<Device>
```

---

## 📦 Installation

### Gradle Kotlin DSL
Add the Encryptable Starter dependency to your Gradle build:
```kotlin
dependencies {
    implementation("tech.wanion:encryptable-starter:1.0.2")
    aspect("tech.wanion:encryptable-starter:1.0.2")
}
```

**Important:** Do not add `spring-boot-starter-web` or `spring-boot-starter-data-mongodb` to your project dependencies, as this may cause version conflicts or duplicate beans.\
The starter already includes these dependencies in compatible versions.

For all system and runtime requirements, see [Prerequisites](docs/PREREQUISITES.md).

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
- [Frequently Asked Questions (FAQ)](docs/FAQ.md)
- [Project Documentation](docs/README.md)
- [Usage Examples](examples/README.md)

---

## ✅ Test Runtime
Test Runtime	✅ 74 tests in 5s\
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
This project was built from the ground up, with AI assistance for documentation and repetitive tasks, to implement zero-knowledge architecture and ORM-like features to the Java and Kotlin ecosystem.

---

## 🤝 Sponsor the author
If you find Encryptable useful, consider sponsoring the author to help fund maintenance, documentation, and professional security audits.

**Why sponsor?** Professional cryptographic audits ($4-6k) enable enterprise adoption and benefit the entire open-source community. Your support helps make zero-knowledge security accessible to everyone.

- [Sponsor on GitHub](https://github.com/sponsors/WanionCane)
- Or click the "Sponsor" button in this repository.

---

## ⚖️ Responsible Use & Ethics
Encryptable is designed to empower privacy, security, and data protection for all users.\
However, as a privacy technology, it can be misused.\
**The author and maintainers of Encryptable do not condone, support, or agree with any harmful, illegal, or unethical usage of this project.\
Users are solely responsible for ensuring their usage of Encryptable complies with all applicable laws and ethical standards.**

*This disclaimer is standard for all privacy technologies and does not imply any unique risk or concern with Encryptable specifically.*

---

## 📝 License
This project is licensed under the MIT License. See [License](LICENCE) for details.
