en# Secret Rotation in Encryptable

## 🎯 Executive Summary

Secret rotation is the process of changing an entity's cryptographic secret while preserving access to encrypted data. In the Encryptable Framework, secrets are user-controlled and derived from credentials, making rotation a straightforward process and enhancing compliance with major regulations.

---

## 📊 Business Justification

### Compliance Requirements - Important Clarification

**CRITICAL UNDERSTANDING:** The Encryptable Framework uses **user-controlled secrets** (normally derived from user credentials), NOT stored encryption keys managed by the application. This distinction is crucial for compliance.

| Regulation | Encryption Key Rotation Requirement | Applies to Encryptable Framework? |
|------------|-------------------------------------|-----------------------------------|
| **PCI-DSS** | Cryptographic keys must be rotated at least annually | ✅ **NO** - User passwords are not "cryptographic keys" managed by the merchant. PCI-DSS 3.5.2 applies to merchant-managed keys (e.g., DEKs, KEKs). User authentication credentials are covered under 8.x requirements. |
| **HIPAA** | Encryption key management recommended 90-180 days | ✅ **NO** - HIPAA Security Rule (§164.312(a)(2)(iv)) addresses "encryption and decryption" but doesn't mandate specific rotation schedules. User-controlled passwords fall under access control (§164.312(a)(1)). |
| **NIST SP 800-57** | Key rotation for symmetric keys based on usage/time | ⚠️ **NUANCED** - NIST distinguishes between system-managed keys and user passwords. User passwords are covered under NIST SP 800-63B (Digital Identity Guidelines), not key management guidelines. |
| **GDPR** | Data protection measures as needed for security | ✅ **YES - BETTER COMPLIANCE** - User-controlled encryption (request-scoped knowledge) provides stronger data protection and aligns with "data minimization" principle (Article 5). |
| **SOC 2** | Security key management | ⚠️ **DEPENDS** - SOC 2 CC6.1 requires logical access controls. User-controlled secrets with prompt-based rotation meets this requirement. |

---

---

## 🏆 Security & Usability Benefits

- **No key storage risk:** Secrets are never stored, only derived
- **User-driven rotation:** Users control when their secret changes
- **Request-scoped knowledge compliance:** No key custodians, no key compromise
- **Simple implementation:** Decrypt with old secret, re-encrypt with new
- **Regulatory alignment:** Meets or exceeds major compliance standards

---

## 🔄 How Secret Rotation Works in Encryptable

> **⚠️ WARNING:** Secret rotation can break entity relationships! If you rotate the secret of an entity that is `@PartOf` another entity (e.g., in a parent-child relationship), or is part of a many-to-many relationship, this will break their bonding. This is because references are encrypted using the entity's secret. After rotation, parent or related entities will no longer be able to reference the rotated entity. **We do not recommend changing the secret of a child entity, or any entity that is part of a many-to-many relationship.**

The Encryptable Framework provides a dedicated method, `rotateSecret`, on the `EncryptableMongoRepository` interface for secure, atomic secret rotation at the entity level. This method:
- Ensures the old secret exists and the new secret does not already exist (prevents accidental overwrite or data loss).
- Finds the entity by the old secret, invokes pre-rotation hooks, and updates the entity to use the new secret.
- Re-encrypts all encrypted fields and associated resources (such as GridFS files) with the new secret.
- Persists the entity with the new secret, making the old secret unusable for access.

**Best Practices:**
- Always call `rotateSecret` within a transactional context to ensure atomicity and rollback on failure.
- For bulk secret rotation, invoke this method for each entity individually.
- Audit secret rotations for compliance and traceability, but never log or store the actual secrets. Instead, log only non-sensitive metadata (e.g., entity type, non-sensitive ID, timestamp, and actor).

**Example audit log entry:**
```
2025-10-23T14:32:10Z [INFO] Secret rotation performed for entity: TestUser, id: 5f8d04e2..., by: adminUser, status: SUCCESS
```

For technical details, see the [rotateSecret method documentation](../src/main/kotlin/tech/wanion/encryptable/mongo/EncryptableMongoRepositoryImpl.kt).

---

## 🎬 Conclusion

Secret rotation in Encryptable Framework is secure, user-driven, and compliance-friendly. By leveraging user-controlled secrets and request-scoped knowledge architecture, the framework eliminates key storage risks and simplifies regulatory alignment for modern applications.

---

## 🔑 Related: Recovery Codes

Recovery codes are a direct application of secret rotation: a high-entropy one-time code is used to decrypt the user's stored secret, after which `rotateSecret` is immediately called to set a new secret.

This pattern allows users to regain access after losing their primary secret, without the server ever storing or learning the secret.

> See **[Recovery Codes](RECOVERY_CODES.md)** for the full implementation pattern, security analysis, and checklist.

