# Encryptable: Compliance Analysis – Alignment with Major Privacy Standards

## 🏆 Executive Summary

Encryptable is designed to meet—and in some cases, exceed—the requirements of major privacy and security standards, including PCI-DSS, HIPAA, GDPR, SOC 2, and LGPD (Brazil). This document summarizes how Encryptable aligns with these standards, highlighting strengths, limitations, and best practices for compliance.

**Proactive Memory Exposure Mitigation:**
Encryptable goes beyond standard cryptographic controls by proactively clearing (zerifying) secrets, decrypted data, and derivation material from JVM memory at the end of each request. This memory cleaning strategy minimizes the risk of sensitive data lingering in memory, reduces the attack surface for heap dumps and forensic analysis, and demonstrates best-in-class privacy hygiene for audit and compliance.

*Due to its strong cryptographic foundation, zero-knowledge architecture, data minimization principles, and memory exposure mitigation, Encryptable is likely compliant with many other privacy and data protection regulations worldwide, beyond those listed here. However, users should always verify compliance with local laws and industry-specific requirements.*

---

## 📋 Alignment with Major Privacy & Security Standards

### 1️⃣ PCI-DSS (Payment Card Industry Data Security Standard)
- **Strong encryption:** All sensitive data can be encrypted with keys derived from user credentials.
- **Zero-knowledge:** The server never stores cryptographic keys or credentials, eliminating insider risk.
- **No key custodians:** No administrators or systems have access to keys, meeting and exceeding PCI-DSS 3.5.2.
- **Key rotation:** Rotation is performed via user password change, which can be aligned with password expiration policies (e.g., 90-180 days), meeting PCI-DSS 3.6.4.
- **No key storage risk:** No need for key-encrypting keys (KEK) or HSM for key storage.
- **Memory exposure mitigation:** All registered secrets and sensitive data are securely wiped from memory after use, reducing risk of recovery from heap dumps or forensic analysis and strengthening compliance posture.

### 2️⃣ HIPAA (Health Insurance Portability and Accountability Act)
- **Data encryption:** All protected health information (PHI) can be encrypted at rest and in transit.
- **Access control:** Per-user cryptographic isolation ensures only authorized users can decrypt their data.
- **No password or key storage:** Reduces risk of credential leaks and insider threats.
- **Key management:** User-driven key rotation aligns with HIPAA recommendations for regular key changes.
- **Memory exposure mitigation:** Proactive memory cleaning ensures PHI and secrets are not left in memory, supporting HIPAA's requirements for safeguarding electronic protected health information (ePHI).

### 3️⃣ GDPR (General Data Protection Regulation)
- **Data minimization:** The system never stores or indexes user credentials or secrets.
- **Right to be forgotten:** Data can be made irretrievable by deleting the user's secret.
- **Strong encryption:** All personal data can be encrypted, supporting GDPR Article 32 (security of processing).
- **Data sovereignty:** Encrypted data is protected regardless of storage location.
- **Memory exposure mitigation:** Sensitive personal data is actively cleared from memory, reducing risk of unauthorized access and supporting GDPR's privacy by design principles.

### 4️⃣ SOC 2 (Service Organization Control 2)
- **Logical access controls:** User-controlled secrets and cryptographic isolation support SOC 2 CC6.1.
- **No key custodians:** Eliminates risk of key compromise by administrators.
- **Auditability:** While the framework does not implement logging, it can be integrated with external audit and monitoring solutions.
- **Memory exposure mitigation:** Automated memory cleaning improves auditability and demonstrates strong controls for privacy and data protection.

### 5️⃣ LGPD (Lei Geral de Proteção de Dados, Brazil)
- **Data minimization:** Encryptable does not store or index user credentials or secrets, supporting LGPD principles of minimal data collection.
- **Strong encryption:** All personal data can be encrypted at rest and in transit, meeting LGPD requirements for data security.
- **Right to erasure:** Data can be made irretrievable by deleting the user's secret, supporting LGPD's right to deletion.
- **User control:** Per-user cryptographic isolation ensures only authorized users can access their own data.
- **Data sovereignty:** Encrypted data remains protected regardless of physical storage location, supporting LGPD's cross-border data transfer requirements.
- **Memory exposure mitigation:** Sensitive data and secrets are proactively cleared from memory, supporting LGPD's requirements for technical measures to protect personal data.

---

## 🚀 Why Zero-Knowledge Architecture Enhances Compliance

### PCI-DSS Compliance ✅

**Requirement 3.5.2:** "Restrict access to cryptographic keys to the fewest number of custodians necessary."

**Encryptable Approach:**
- ✅ **Zero custodians**—Not even system administrators have access to keys
- ✅ **Keys derived from user credentials**—No key storage = no key custodians
- ✅ **Exceeds the standard** by eliminating custodian access entirely

**Requirement 3.5.3:** "Store secret and private keys in one or more of the following forms: Encrypted with a key-encrypting key, within a secure cryptographic device, or as at least two full-length key components."

**Encryptable Approach:**
- ✅ **Keys never stored**—Derived on-demand from user password via HKDF
- ✅ **Exceeds requirement**—No storage means no risk of key compromise from storage

**Requirement 3.6.4:** "Cryptographic keys are rotated at least annually."

**Encryptable Approach:**
- ✅ **User password rotation policy**—Enforce password changes (maps to key rotation)
- ✅ **Better security model**—User-initiated rotation is more secure than automatic rotation
- ✅ **Compliance strategy:** Implement password expiration policy (e.g., 90-180 days)

**PCI-DSS Compliance Strategy Example:**
```kotlin
// Enforce password rotation = key rotation
@Configuration
class PasswordPolicy {
    val maxPasswordAgedays = 90  // Aligns with PCI-DSS annual requirement
    fun enforceRotationPolicy(user: User): Boolean {
        // ...implementation...
    }
}
```

---

## ⚠️ Limitations & Considerations

| Standard | Covered by Framework | Additional Controls Needed |
|----------|---------------------|---------------------------|
| PCI-DSS  | ✅ Encryption, key mgmt | ❌ Logging, network, ops |
| HIPAA    | ✅ Data protection      | ❌ Monitoring, recovery  |
| GDPR     | ✅ Data minimization   | ❌ Consent, breach notif |
| SOC 2    | ✅ Access control      | ❌ Audit, monitoring     |
| LGPD     | ✅ Data minimization, security | ❌ Consent, breach notification, DPO |

- **Compliance depends on usage:** The framework covers data protection and key management, but full compliance also requires logging, access control, network segmentation, vulnerability testing, and more.
- **Audit & monitoring:** The framework does not directly implement access logging or security alerts, which are required by most standards.
- **Data recovery:** Since keys are derived from credentials, loss of the password may render data unrecoverable. Robust recovery processes are required.
- **Memory exposure mitigation:** Encryptable's proactive memory cleaning goes beyond most frameworks, but users should still ensure that application-level code does not retain sensitive data longer than necessary.

---

## 🎯 Conclusion

Encryptable provides a strong foundation for meeting the sensitive data protection and key management requirements of PCI-DSS, HIPAA, GDPR, SOC 2, and LGPD. However, full compliance depends on additional controls implemented in the client environment, such as logging, monitoring, access control, and operational policies.

---

- [🔄 Secret Rotation: Secure, User-Driven, and Compliance-Friendly](SECRET_ROTATION.md) — How Encryptable enables safe, auditable secret rotation without key storage risk

*This document should be reviewed by compliance experts before being used in official audits.*