# 📚 Encryptable Documentation Index

## 🏯 Executive Summary

Welcome to the Encryptable documentation hub. Here you'll find comprehensive guides, technical analyses, and innovative concepts powering Encryptable. Each document is crafted to illuminate a unique aspect of the project, from cryptography to compliance, security, and beyond.

---

## 📖 Documentation Overview

| Document                                                             | Description                                                           |
|----------------------------------------------------------------------|-----------------------------------------------------------------------|
| [AI_SECURITY_AUDIT.md](AI_SECURITY_AUDIT.md)                         | AI-assisted security audit and vulnerability analysis                |
| [BEST_PRACTICES.md](BEST_PRACTICES.md)                             | Secure memory handling best practices: zerifying secrets, decrypted data, and derivation material |
| [CACHING_SECRETS_RISKS.md](CACHING_SECRETS_RISKS.md)                 | Risks of caching secrets and mitigation strategies                    |
| [CID_COLLISION_ANALYSIS.md](CID_COLLISION_ANALYSIS.md)               | Analysis of CID collision risks and mitigation                        |
| [CID_COMPACTNESS.md](CID_COMPACTNESS.md)                             | CID Compactness: Why CID is Shorter than UUID                         |
| [COMPLIANCE_ANALYSIS.md](COMPLIANCE_ANALYSIS.md)                     | Compliance with HIPAA, PCI-DSS, and other standards                   |
| [CONFIGURATION.md](CONFIGURATION.md)                                 | Framework configuration options, defaults, and tuning                 |
| [COROUTINES_INCOMPATIBILITY.md](COROUTINES_INCOMPATIBILITY.md)       | Why coroutines are incompatible with Encryptable and must not be used |
| [CRYPTOGRAPHIC_ADDRESSING.md](CRYPTOGRAPHIC_ADDRESSING.md)           | Cryptographic addressing and HKDF-based deterministic CIDs            |
| [DELETING_ENTITIES_WITHOUT_SECRETS.md](DELETING_ENTITIES_WITHOUT_SECRETS.md)        | Deleting temporary/expiring entities without knowing their secrets      |
| [DESIGN_ANALYSIS_SECRETS_VS_IDS.md](DESIGN_ANALYSIS_SECRETS_VS_IDS.md) | Architectural deep dive: Why secrets instead of IDs                   |
| [ENCRYPTED_MEMORY_ENCLAVES.md](ENCRYPTED_MEMORY_ENCLAVES.md)         | Encrypted memory enclaves for enhanced runtime security               |
| [FAQ.md](FAQ.md)                                                     | Frequently Asked Questions about Encryptable                          |
| [INNOVATIONS.md](INNOVATIONS.md)                                     | Technical innovations and novel contributions of Encryptable          |
| [LIMITATIONS.md](LIMITATIONS.md)                                     | Known limitations and trade-offs of the framework                     |
| [MEMORY_HIGIENE_IN_ENCRYPTABLE.md](MEMORY_HIGIENE_IN_ENCRYPTABLE.md) | Advanced memory hygiene strategies and automated buffer wiping        |
| [MIGRATING_FROM_OTHER_VERSIONS.md](MIGRATING_FROM_OTHER_VERSIONS.md) | Migration guides for upgrading between Encryptable versions           |
| [ORM_FEATURES.md](ORM_FEATURES.md)                                   | ORM-like features for MongoDB relationship management                 |
| [POWER_OF_TOUCH.md](POWER_OF_TOUCH.md)                               | Practical guide to the touch method: audit, security, automation     |
| [PREREQUISITES.md](PREREQUISITES.md)                                    | System & runtime requisites: JVM args, minimum Java version, dependencies, and setup |
| [SECRET_ROTATION.md](SECRET_ROTATION.md)                             | Secret rotation process, compliance, and best practices               |
| [SECURITY_WITHOUT_AUDIT.md](SECURITY_WITHOUT_AUDIT.md)               | Security analysis without formal audit considerations                 |
| [SECURITY_WITHOUT_SECRET.md](SECURITY_WITHOUT_SECRET.md)             | Security architecture without persistent secrets                      |
| [SPONSORSHIP_GOALS.md](SPONSORSHIP_GOALS.md)                         | Sponsorship tiers, funding strategy, and transparent budgets          |
| [WHO_WOULD_VALUE_THIS_FRAMEWORK.md](WHO_WOULD_VALUE_THIS_FRAMEWORK.md) | Target users and organizations for Encryptable                        |
| [WHY_AVOIDING_STRINGS_IS_HARD_IN_JAVA.md](WHY_AVOIDING_STRINGS_IS_HARD_IN_JAVA.md) | Technical challenges of avoiding String types in Java/Kotlin          |

### 💡 Concepts (Ideas, Proposals, and Vision)
| Document | Description |
|----------|-------------|
| [DETERMINIST_CRYPTOGRAPHY.md](concepts/DETERMINIST_CRYPTOGRAPHY.md) | Deterministic cryptography for zero-knowledge security |
| [NO_PASSWORD_STORAGE.md](concepts/NO_PASSWORD_STORAGE.md) | Eliminating password storage risks |
| [ULTIMATE_SECURITY.md](concepts/ULTIMATE_SECURITY.md) | Achieving ultimate security in data management |
| [USER_CENTRIC_SECURITY.md](concepts/USER_CENTRIC_SECURITY.md) | User-centric security models and per-user isolation |
| [WHY_ZERO_KNOWLEDGE_SHOULD_BE_THE_NORM.md](concepts/WHY_ZERO_KNOWLEDGE_SHOULD_BE_THE_NORM.md) | Why zero-knowledge architecture should be the standard |
| [WORLD_WITH_ZERO_KNOWLEDGE.md](concepts/WORLD_WITH_ZERO_KNOWLEDGE.md) | Vision for a world with zero-knowledge security |
| [ZERO_KNOWLEDGE_2FA.md](concepts/ZERO_KNOWLEDGE_2FA.md) | Zero-knowledge two-factor authentication concepts |
| [ZERO_KNOWLEDGE_2FA_APP.md](concepts/ZERO_KNOWLEDGE_2FA_APP.md) | Application-level implementation of zero-knowledge 2FA |
| [ZERO_KNOWLEDGE_AUTH.md](concepts/ZERO_KNOWLEDGE_AUTH.md) | Zero-knowledge authentication mechanisms |

---

## 🧪 Test Suite (build/test-results)

Run the full test suite to ensure Encryptable functions as expected. The tests cover encryption, decryption, data integrity, and performance benchmarks.
More details [Here](/src/test/kotlin/cards/project/README.md).


---

## 🌟 How to Use This Index

- Click any document title to explore its content.
- Use this index to navigate the technical, compliance, and innovation landscape of Encryptable.
- For a deep dive into innovations, start with [Innovations](INNOVATIONS.md).

---

## 💡 Related Topics

- [Encryptable Main README](../README.md)
- [Basic Usage Examples](../examples/README.md)

---

*Empowering secure, zero-knowledge applications for the future.*