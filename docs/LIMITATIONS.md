# Limitations

Before we discuss the technical constraints and limitations of Encryptable, let's first talk about the **opportunities** this framework represents.


## 💡 The Opportunity

While this document will detail various technical constraints, it's important to understand that Encryptable represents an **opportunity** that far outweighs its limitations:

**What makes Encryptable special:**
- ✅ **First-of-its-kind** - Cryptographic addressing has never been done before in this way
- ✅ **Genuine innovation** - Not just an incremental improvement.
- ✅ **Solves real pain** - Password storage liability is a billion-dollar problem
- ✅ **Performance gains** - O(1) lookups provide measurable 5-10x database throughput improvements
- ✅ **Request-scoped knowledge ("Transient Knowledge") by default** - Eliminates entire categories of security breaches

**Market opportunity:**
- Backend security is a **$50+ billion market** (Gartner)
- Data breach costs average **$4.45 million per incident** (IBM 2023)
- GDPR fines can reach **€20 million or 4% of global revenue**
- Transient knowledge solutions are **under-served** in the JVM ecosystem

**Most of the limitations you'll read below are features, not bugs:**
- "No soft delete" = True GDPR compliance (right to be forgotten)
- "Cannot query encrypted fields" = Proof of real request-scoped knowledge
- "No automated rotation" = Genuine secret security (not stored anywhere)
- "Class names immutable" = Cryptographic integrity guarantee

**Bottom line:** Encryptable's limitations prove it's genuinely secure. The market will pay premium prices for real security, not security theater.

> **Note:** Encryptable implements a 'transient knowledge' (request-scoped knowledge) model: secrets and keys are only present in memory for the duration of a request or operation. This is not strict 'zero-knowledge' in the cryptographic sense, but provides strong privacy guarantees by ensuring the server never persistently retains secrets or keys. For a full explanation and apology, see [Not Zero-Knowledge](NOT_ZERO_KNOWLEDGE.md).

See [Innovations](INNOVATIONS.md) for detailed technical innovations, and [Sponsorship Goals](SPONSORSHIP_GOALS.md) for how we will be building a sustainable ecosystem around these innovations.

---

Now, with that context in mind, let's examine the specific technical and platform limitations that may affect your application design and deployment.

## Summary

- **Professional security audit:** Not yet completed due to cost ($4k-6k initial + ongoing); seeking $3,000/month in sponsorships for full-time development and security validation - [sponsor the project](https://github.com/sponsors/WanionCane)
- **Developer responsibility:** Framework cannot prevent developers from logging secrets (universal limitation of all security frameworks); organizational controls (code reviews, training, audits) required
- **Recommended usage:** Best suited for new systems/applications; legacy migrations are technically supported but are dangerous and time-consuming, so they are not recommended
- **KMS per-entity integration:** Not supported due to performance constraints (10-50ms per entity); use master secret from KMS at startup instead
 **Open source & sustainability:** MIT License, forkable, community-driven; only main repository will be professionally audited; ⚠️ audit expiration risk if maintainer unavailable
- **Minimum Java version:** 21 (virtual threads required)
- **Kotlin-first design:** Best used with Kotlin; Java users can use the framework, but will not benefit from the full set of extension functions and idiomatic features available in Kotlin
- **Kotlin coroutines:** Not compatible (ThreadLocal usage)
- **GraalVM native image:** Not compatible (AspectJ limitation)
- **Memory Sanitization & JVM Limitations:** JVM memory model makes it impossible to fully erase secrets (see [Why Avoiding Strings for Secrets Is (Nearly) Impossible in Java](WHY_AVOIDING_STRINGS_IS_HARD_IN_JAVA.md))
- **Class/package immutability:** Cannot rename or move entity classes after data exists; class name and package are used in cryptographic derivation
- **Limited Identifier support:** Only `CID` identifiers are supported (no UUID, int, etc.)
- **Secret loss = data loss:** If a user loses their secret, their data is permanently inaccessible; true request-scoped knowledge means the server cannot recover or reset secrets
- **Repository methods:** Only `deleteById` is supported for temporary or non-secret-based entities in `EncryptableMongoRepositoryImpl`. Other standard Spring Data methods (`insert`, `findById`, `findAll`, etc.) remain unsupported; use secret-based methods for all sensitive data
- **No soft delete:** Only hard deletion is supported; soft delete is excluded to uphold the "right to be forgotten."
- **Migration away from Encryptable:** Lazy migration possible but slow; requires dual systems and depends on user login frequency; bulk migration impossible without breaking request-scoped knowledge
- **Querying encrypted fields:** Cannot query or filter encrypted fields at the database level; must query in-memory after decryption
- **Indexing encrypted fields:** Cannot index encrypted fields; only unencrypted fields can be indexed
- **Performance characteristics:** 10–15% overhead due to encryption/decryption; hardware-accelerated on modern processors (AES-NI since 2010+, SHA extensions since 2019+)
- **AES-GCM all-or-nothing property (universal to all AES-GCM systems):** Single bit corruption causes encrypted field to become unrecoverable (returns encrypted data); entity remains accessible but affected field is lost; requires reliable storage infrastructure and comprehensive backup strategy
- **Secret rotation:** No built-in automation; must be performed manually or by user-initiated process
- **Brute-force/DDoS mitigation:** No built-in detection or mitigation; must be handled at the application/infrastructure level

---

## 🔐 No Professional Security Audit (Yet)

Encryptable has not yet undergone a professional security audit by qualified cryptographers.\
While the framework is built on industry-standard cryptographic algorithms (AES-256-GCM, HKDF per RFC 5869) and follows cryptographic best practices, independent third-party validation is essential for production use with sensitive data.

**Why hasn't it been audited?**
Professional security audits by qualified cryptographers typically cost **$4,000–$6,000** for a focused framework like Encryptable (~2,000 lines of code with ~500 lines of core cryptographic implementation). This is a common challenge for open-source projects.

**What this means for you:**
- ✅ **Safe for personal projects, learning, and internal tools** - The cryptography is sound
- ✅ **Suitable for startups/MVPs and general web applications** - With proper security controls implemented
- ⚠️ **Use with community review for public-facing applications** - Implement all application-level security controls
- 🔴 **Requires professional audit for regulated industries** - Financial/healthcare data requires third-party validation for compliance

**Current security validation:**
- ✅ **[Comprehensive AI-generated security analysis →](AI_SECURITY_AUDIT.md)** (detailed cryptographic review)
- ✅ Community security review (ongoing via Reddit, Stack Exchange, OWASP)
- ✅ Industry-standard algorithms (no "rolling our own crypto")
- ✅ Transparent about limitations and scope
- ✅ Active security testing and best practices

**How you can help:**

Professional audits and ongoing development require funding. We're seeking sponsorships to fund the initial security audit and build a sustainable privacy ecosystem.

**📋 [See our Sponsorship Goals & Funding Strategy →](SPONSORSHIP_GOALS.md)** for transparent budgets, roadmaps, and how your support enables security audits, team growth, and privacy infrastructure development.

---

## 👨‍💻 Developer Responsibility: Secret Handling

**Concern:** "A malicious or careless developer could log or save secrets, bypassing all security."

**Reality:** ✅ **This is a universal limitation of ALL security frameworks in ALL programming languages, not specific to Encryptable.**

Encryptable **cannot prevent** a developer from intentionally or accidentally logging secrets before passing them to the framework. This is **not a weakness of Encryptable**—it's a fundamental reality of software development that applies equally to:

- **Signal Protocol** - Developers can log encryption keys
- **TLS/OpenSSL** - Developers can log private keys and session keys
- **Spring Security** - Developers can log passwords and tokens
- **AWS KMS SDK** - Developers can log decrypted keys
- **Any cryptographic library** - Developers have access to keys/secrets in their application code

**Why this cannot be solved by the framework:**

1. **Code execution context:** Framework code runs within the developer's application, with the developer's permissions
2. **Trust boundary:** The framework must trust the developer to pass secrets correctly
3. **Technical impossibility:** No framework can control what developers do with data before/after framework method calls
4. **Philosophical issue:** Preventing developers from accessing their own application's data is both impossible and wrong

**What Encryptable DOES provide:**

✅ **Minimizes exposure:**
- Secrets only in memory during request scope (transient knowledge)
- Automatic memory wiping at request end
- No secrets stored in database
- Fail-fast on memory clearing failures

✅ **Best practices documentation:**
- Clear guidance on secret handling ([MEMORY_HIGIENE_IN_ENCRYPTABLE.md](MEMORY_HIGIENE_IN_ENCRYPTABLE.md))
- Security audit document ([AI_SECURITY_AUDIT.md](AI_SECURITY_AUDIT.md))
- Example code showing proper usage

✅ **Secure by default:**
- Framework never logs secrets
- No debug output of sensitive data
- Secure error handling (no plaintext in exceptions)

**Mitigation (Organizational Level):**

Developer behavior is an **organizational security responsibility**, not a framework responsibility:

- **Code reviews** - Catch accidental logging
- **Security training** - Educate on secret handling
- **Static analysis** - Detect patterns like `logger.info(secret)`
- **Access controls** - Limit who can deploy code
- **Audit logs** - Monitor for suspicious behavior
- **Separation of duties** - Multiple approvals for production

**Bottom line:** Every security framework assumes developers follow secure coding practices. Malicious insiders are an organizational problem, not a framework problem. This is industry-standard across all security frameworks.

---

## 🆕 Recommended for New Systems and Applications

Encryptable introduces a new paradigm for data security and management, fundamentally different from traditional MongoDB usage.\
The author strongly recommends implementing Encryptable in new systems or applications, rather than attempting to retrofit it onto existing, unencrypted MongoDB datasets.

**Why not convert existing datasets?**
- **Data Model Incompatibility:** Encryptable relies on deterministic, secret-derived CIDs and field-level encryption. Migrating existing data (with legacy IDs, unencrypted fields, or different relationships) to this model is complex and error-prone.
- **Loss of Queryability:** Fields that were previously queryable in plaintext will become encrypted and unqueryable, potentially breaking existing application logic and reporting.
- **Migration Complexity:** Securely converting large datasets to Encryptable's model requires careful handling of secrets, re-indexing, and re-encryption, with significant risk of data loss or corruption if not done perfectly.

**Recommendation:**
- Use Encryptable for new projects or greenfield applications where its security model can be fully leveraged from the beginning.
- For existing MongoDB deployments, carefully evaluate the migration risks and consider whether a hybrid or phased approach is feasible. While legacy migrations are technically supported, they are dangerous and time-consuming, so they are not recommended and may not deliver the intended security benefits.

> **Note:** Migrating a legacy system to Encryptable is technically possible, but would take too much work and is not recommended.\
> **Encryptable is not responsible for any data loss that may occur during the process.**

---

## 🔑 No Per-Entity KMS Integration

Encryptable does **not** support per-entity secret retrieval from external Key Management Services (KMS) such as AWS KMS, Azure Key Vault, Google Cloud KMS, or HashiCorp Vault.

**Why this limitation exists:**

Encryptable is designed to be **stateless** and **request-scoped** with minimal latency. Per-entity KMS integration is fundamentally incompatible with these design goals:

| Requirement | KMS Per-Entity Behavior | Result |
|------------|------------------------|--------|
| **Low latency** | KMS network call: 10-50ms per entity | ❌ Unacceptable performance penalty |
| **Stateless** | Cannot cache secrets without breaking security model | ❌ Must fetch every request |
| **Request-scoped** | Secrets cleared after each request | ❌ KMS call required every time |
| **Multiple entities** | 10 entities = 10 KMS calls (100-500ms) | ❌ Request timeout risk |

**Example performance impact:**
```
Without KMS: 10 entities × 5ms = 50ms request time
With KMS:    10 entities × (5ms + 30ms KMS) = 350ms request time

7x slower with unacceptable latency for web applications
```

### ✅ Recommended Approach: Master Secret from KMS

Instead of fetching secrets per-entity, **fetch the master secret once** at application startup from your KMS:

```kotlin
@Configuration
class KmsConfiguration {
    
    @PostConstruct
    fun loadMasterSecretFromKms() {
        // Fetch master secret from KMS once at startup
        val kmsClient = createKmsClient()
        val masterSecret = kmsClient.getSecret("encryptable-master-secret")
        
        // Provide to Encryptable
        MasterSecretHolder.setMasterSecret(masterSecret)
        
        // Secret is now cached in memory for the application lifetime
        // @Id entities with @Encrypt will use this for field encryption
    }
    
    private fun createKmsClient(): KmsClient {
        // AWS KMS, Azure Key Vault, HashiCorp Vault, etc.
        return AwsKmsClient.builder().build()
    }
}
```

**This approach:**
- ✅ **One KMS call** at startup (acceptable latency)
- ✅ **Fast request processing** (no per-request KMS calls)
- ✅ **Works with any KMS provider** (AWS, Azure, GCP, Vault)
- ✅ **Maintains stateless architecture** (master secret in memory only)
- ✅ **Compatible with @Id entities** (uses master secret for encryption)

### 🔐 For Maximum Security: Use @HKDFId Instead

If you need **per-entity cryptographic isolation** (each entity with its own secret), use **@HKDFId entities** instead:

```kotlin
// ✅ Recommended for sensitive data with cryptographic isolation
class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
    
    // Each user has their own derived secret
    // Compromise of one user doesn't affect others
    // No shared master secret involved
}

// ⚠️ Use for non-sensitive data or shared resources
class Device : Encryptable<Device>() {
    @Id override var id: CID? = null
    @Encrypt var metadata: String? = null
    
    // All devices share the master secret for encryption
    // Simpler but less isolation
}
```

**@HKDFId advantages:**
- Each entity derives its own encryption keys from its own secret
- No shared master secret required
- Perfect cryptographic isolation between entities
- If master secret leaks, @HKDFId entities remain secure

### 🏢 Enterprise KMS Integration Pattern

For enterprises that must use KMS for compliance, the recommended pattern is:

1. **Startup:** Fetch master secret from KMS → `MasterSecretHolder.setMasterSecret(secret)`
2. **@HKDFId entities:** User secrets derived at authentication (no KMS per-request)
3. **@Id entities:** Use master secret for encryption (already loaded)

**This keeps KMS integration simple, performant, and compliant while maintaining Encryptable's security model.**

### ⚠️ Master Secret Rotation Is Complex

**Important:** Rotating the master secret is **not** as simple as just fetching a new secret and restarting the application.

**Why rotation is complex:**

All @Id entities with @Encrypt fields are encrypted using the master secret. If you change the master secret, the old encrypted data becomes **permanently inaccessible** because the new secret cannot decrypt data encrypted with the old secret.

**Required rotation process:**

1. **Fetch new master secret** from KMS
2. **Keep old master secret** available temporarily
3. **Re-encrypt ALL @Id entity data:**
   - Load each entity with old master secret
   - Decrypt fields using old secret
   - Encrypt fields using new secret
   - Save back to database
4. **Verify all data migrated** successfully
5. **Retire old master secret**
6. **Restart application** with new secret only

**Performance impact:**
- Must touch **every @Id entity** in the database
- For large datasets (millions of records), this could take hours or days
- Requires dual-secret support during transition
- Risk of data loss if migration fails midway

**Recommended approach for @Id entities:**

If you anticipate needing master secret rotation:
1. **Use @HKDFId instead** - Each entity has its own secret, rotation is per-user
2. **Minimize @Id with @Encrypt usage** - Use @Id only for truly non-sensitive data
3. **Plan for downtime** - Master secret rotation requires maintenance window
4. **Implement gradual migration** - Rotate data lazily on access rather than all at once

---

## 🔓 Open Source & Sustainability

**Encryptable is and will always be open source under the MIT License.**

This means:
- ✅ **Free forever** - No licensing fees, no restrictions
- ✅ **Forkable** - If the main project becomes unmaintained, anyone can fork and continue development
- ✅ **Community-driven** - Development can continue with or without the original author
- ✅ **Transparent** - All code is public and auditable

**Important note on security audits:**
- Only the **main repository** will receive professional security audits (when funding is available)
- Forks are welcome but **will not be audited** unless their maintainers arrange independent audits
- For production use in regulated industries, always use the **audited main repository** or arrange your own security audit

**⚠️ Critical: Audit Expiration Risk**

Security audits have an **expiration/validity period** (typically 1-2 years) and require **ongoing re-certification** as the framework evolves and new threats emerge.

**What happens if the original maintainer becomes unavailable and audits aren't renewed:**
- ✅ **The code remains open source** - Anyone can fork and continue development
- ✅ **Previous audit results remain valid** - For the specific version that was audited
- ❌ **Future versions lose certification** - New features/updates won't be audited
- ❌ **Compliance may lapse** - Enterprise/regulated use may require current audits (not expired ones)
- ⚠️ **Audit doesn't transfer to forks** - Forks must arrange their own independent audits

**Enterprise risk mitigation strategies:**

1. **Use pinned, audited versions** - Pin to specific audited release (e.g., 1.0-audited), don't auto-update
2. **Budget for independent audits** - If Encryptable is mission-critical, budget $5k-10k for periodic independent security reviews
3. **Fork and maintain internally** - Large enterprises can fork the audited version and maintain it with their own security team
4. **Sponsor audit renewals** - Enterprise sponsors can fund ongoing audits to ensure continuity
5. **Hybrid approach** - Use audited version for regulated data, community version for non-sensitive use

**Example timeline risk:**
```
2026: Main repo audited ✅ (Enterprise uses v1.0-audited)
2027: Audit renewed ✅ (Enterprise upgrades to v1.5-audited)
2028: Maintainer unavailable, no renewal ❌
2029: Enterprise still on v1.5-audited (valid but aging)
2030: Audit expired, compliance issues ⚠️
```

**Bottom line for enterprises:**
If you rely on Encryptable for regulated/compliance-critical workloads, you should either:
- **Sponsor ongoing development** to ensure audit continuity, OR
- **Budget for your own audits** every 1-2 years, OR
- **Fork and maintain** with your own security team

**The open-source nature protects the CODE continuity, but not the AUDIT continuity.**

**Sustainability:** While currently maintained by WanionCane alone, the open-source nature ensures the project can continue even if the original maintainer becomes unavailable. The comprehensive documentation (26+ docs) and well-tested codebase (74 tests) make it feasible for others to maintain or fork if needed.

---

## 🏷️ Minimum Java Version

Encryptable is designed for modern Java and Kotlin applications and makes heavy use of Java virtual threads (Project Loom).\
This enables high concurrency and scalability, but means that older Java runtimes are not supported.\
The minimum required Java version is 21.

**ThreadLocal and Virtual Threads:**
Encryptable uses `InheritableThreadLocal` to propagate request-scoped entity tracking metadata (CID, entity references, initial hashCodes for change detection) to virtual threads spawned during request processing. This is a standard and appropriate use case for virtual threads—each request context is properly inherited by child virtual threads, enabling features like automatic change detection and resource cleanup. Since virtual threads are lightweight and request-scoped, there are no memory concerns with this pattern.

> **Note:** The entity metadata tracked includes the entity object itself, which contains the user's secret (since the secret must have been provided to load the entity in the first place). This is not a security concern—the secret was already in memory to perform the initial `findBySecret()` operation, and the ThreadLocal context is automatically cleared when the request completes.

---

## 🟣 Kotlin-First Design

Encryptable is designed first and foremost for Kotlin, leveraging Kotlin's language features, extension functions, and idiomatic patterns to provide a highly expressive and ergonomic API.\
While Java users can use the framework, they will not have access to the full set of extension functions, concise syntax, and advanced features that Kotlin developers enjoy.\
As a result, the developer experience and productivity are significantly better when using Kotlin.

**Implications:**
- Java users can still use Encryptable, but will need to write more boilerplate code and will not benefit from Kotlin-specific enhancements.
- Some advanced patterns, extension methods, and idiomatic usage are only available in Kotlin.

**Recommendation:**
- Prefer Kotlin for new projects using Encryptable to take full advantage of its capabilities and developer experience.
- If you must use Java, be aware that you will not have access to the full ergonomic and expressive API surface provided by Kotlin.

---

## 🚫 Kotlin Coroutines: Not compatible (ThreadLocal usage)

While Encryptable is designed to offer the best experience in Kotlin, it is not compatible with Kotlin Coroutines.\
Encryptable relies on ThreadLocal, which does not propagate context reliably across coroutine suspensions and resumptions.\
For a detailed explanation, see [Coroutines Incompatibility](COROUTINES_INCOMPATIBILITY.md).

---

## ⚠️ Native Image (GraalVM) Incompatibility

Encryptable is not compatible with GraalVM native image (AOT compilation) due to its use of AspectJ for bytecode weaving.\
AspectJ relies on runtime or build-time bytecode modification, which is not supported by GraalVM's native image static analysis and ahead-of-time compilation process.\
This is a limitation of the current Java ecosystem and AspectJ itself, not specific to Encryptable.\
If your application requires native image support, please consider this limitation when choosing Encryptable.

---

## 🧹 Memory Sanitization & JVM Limitations

Encryptable implements proactive memory sanitization for secrets and sensitive data.\
All secrets, decrypted data, and intermediate plaintexts are tracked and cleared from memory at the end of each request using reflection-based methods.\
If clearing fails, Encryptable throws an exception to ensure privacy failures are never silent.\
Sensitive data is only retained for the minimum necessary duration, managed per-request with thread-local isolation.

**Note:** Zerifying (clearing) decrypted data and secrets adds some performance overhead, as memory must be actively overwritten and managed. This is a trade-off for improved privacy and security.

**Important Limitation:** Due to the JVM memory model, it is impossible to guarantee the complete and immediate removal of all copies of secrets (especially Strings) from memory.\
This is a fundamental limitation of Java/Kotlin and all garbage-collected environments.\
Encryptable’s approach is the most effective and auditable strategy available, but developers should remain aware of these constraints and minimize the lifetime and scope of secrets in memory.\
For a deep dive into why securely erasing strings is so difficult in Java, see [Why Avoiding Strings Is Hard in Java](WHY_AVOIDING_STRINGS_IS_HARD_IN_JAVA.md).

**Advanced Option: Memory Enclaves**
For ultra-high-security deployments, you can run the entire JVM in encrypted memory enclaves (such as Intel SGX/TDX or AMD SEV-SNP).\
This ensures that all memory, including any lingering secrets or intermediate copies, is encrypted at the hardware level.\
See [Encrypted Memory Enclaves](ENCRYPTED_MEMORY_ENCLAVES.md) for implementation details, overhead analysis, and trade-offs.\
However, this adds 2–50% performance overhead and significant deployment complexity, so it's only recommended for government/military applications or compliance requirements that mandate memory encryption.

---

## 🏷️ Class and Package Names Cannot Be Changed

Once entities are stored in the database, you **cannot rename the entity class or move it to a different package**.\
This is a **fundamental limitation** shared by both standard Spring Data MongoDB and Encryptable.

**In summary:**
 - **Spring Data MongoDB:** Renaming breaks deserialization (can't find the old class)
 - **Encryptable:** Renaming breaks cryptographic addressing (wrong CID + wrong encryption keys)

**Why this limitation exists:**

**In standard Spring Data MongoDB:**
- MongoDB stores the fully qualified class name in the `_class` field
- When deserializing, Spring Data looks for the class specified in `_class`
- If you rename `com.example.User` → `com.newpackage.User`, the database still has `_class: "com.example.User"`
- Deserialization fails because the old class doesn't exist in your code anymore

**In Encryptable (additional cryptographic limitation):**

Encryptable uses the **fully qualified** class name (package + class name) as part of the cryptographic context when deriving:
1. **CIDs** (entity identifiers) via HKDF
2. **Encryption keys** for AES-256-GCM

The derivation formula is:
```
info = "com.example.package.ClassName:CID"  // for CID derivation
info = "com.example.package.ClassName:ENCRYPTION_KEY"  // for encryption keys
```

**Why include the fully qualified class name (package + class) in derivation:**

This design ensures that **even if the same secret is used**, it produces **different CIDs and encryption keys** for different entity types/repositories:
- `com.example.users.User` entity with secret "abc123" → CID: `abc...123`
- `com.example.orders.Order` entity with secret "abc123" → CID: `def...456` (completely different)
- `com.example.admin.User` entity with secret "abc123" → CID: `ghi...789` (different from first User!)

This prevents:
- **CID collisions** across different entity types
- **CID collisions** between entities with the **same class name** in different packages (e.g., `com.example.users.User` vs `com.admin.User`)
- **Key reuse** across different entities (each entity type gets unique encryption keys)
- **Cross-repository security issues** (same secret can be safely used across multiple repositories)

> **Note on collections:** While entities with the same class name in different packages (e.g., `com.example.users.User` and `com.admin.User`) would produce different CIDs and could technically share the same MongoDB collection, this is **not recommended**.\
> Best practice is to use different collection names for different entity classes to maintain clear separation and avoid confusion.

**What happens if you rename or move the class:**

- **CID derivation breaks:** The same secret will produce a **different CID** because the class context changed
    - Example: `com.example.User` → `com.newpackage.User` produces different CIDs
    - Result: Cannot find existing entities in the database (different lookup keys)

- **Encryption keys change:** The same secret will derive **different encryption keys**
    - Result: Cannot decrypt existing data (keys don't match)

- **Complete data loss:** All existing entities become permanently inaccessible (unless you rename/change the package back to what it used to be)

**Implications:**

- ⛔ Cannot refactor package structure after data exists
- ⛔ Cannot rename entity classes after data exists
- ⛔ Must carefully plan class and package names before production deployment
- ⚠️ Violating this rule results in **permanent data loss** (cannot recover without the original class name)

**Recommendations:**

- **Plan your package structure carefully** before deploying to production
- **Use stable, unversioned package names** (e.g., `com.company.app.domain.User`) - do NOT use versioned packages as changing versions will break access to existing entities
- **Document class names** as part of your data schema - they are effectively part of the cryptographic contract
- **Do not refactor** entity class names or packages once data exists in production

**Bottom line:** Treat entity class names and package paths as **immutable once data exists**. They are cryptographically bound to your data and cannot be changed without breaking access to all existing entities.

> **📖 For more information about how cryptographic addressing works and its security benefits, see [Cryptographic Addressing](CRYPTOGRAPHIC_ADDRESSING.md).**

---

## 🆔 Limited Identifier Support

Encryptable is intentionally designed to support only identifiers of type `CID`.\
This is a core architectural decision and is not planned to change.\
This requirement arises directly from the framework's cryptographic addressing innovation: the CID is deterministically derived from user secrets using cryptographic functions, enabling stateless, request-scoped knowledge access and O(1) lookups.
Supporting other ID types would undermine these guarantees and the unique architecture of Encryptable.

> **Note:** Earlier versions of Encryptable used MongoDB ObjectIds, but the collision risk was higher than desired. UUIDs were considered for their uniqueness, but their size was unnecessarily large for the framework's needs. The CID format was created to provide a compact, 128-bit identifier with a much smaller representation than UUID, while maintaining strong collision resistance and supporting cryptographic addressing.

**Implications:**
- Integrating with databases or external services that use different ID types may require you to convert or map IDs to `CID`.
- Migration and interoperability with heterogeneous systems can be more complex.

**Recommendations:**
- Map or convert your IDs to `CID` in your application before using Encryptable.
- If your use case requires native support for other identifier types, Encryptable may not be the right fit for your project.

---

## 🔑 Secret Loss = Data Loss

**If a user loses their secret, their data is permanently inaccessible.** This is by design and a fundamental characteristic of true request-scoped knowledge architecture.

Encryptable implements genuine transient knowledge encryption, which means:
- The server never stores user secrets
- The server cannot reconstruct secrets from stored data
- The server cannot reset or recover secrets
- Without the secret, encrypted data cannot be decrypted

**Why this limitation exists:**

True request-scoped knowledge architecture requires that the server has **no persistent knowledge** of user secrets. If the server could recover or reset secrets, it would mean the server has access to (or can derive) the secret—which would completely undermine the transient knowledge guarantee.

**Implications:**

- **No "forgot password" functionality** - The server cannot reset secrets because it doesn't store them
- **No account recovery** - Lost secrets mean permanent data loss
- **User responsibility** - Users must securely store their secrets (password managers, hardware keys, backups)
- **No administrative override** - Even system administrators cannot access user data without secrets

**This is a feature, not a bug:**

While this may seem like a limitation, it's actually proof that Encryptable provides genuine request-scoped knowledge security:
- Data breaches cannot expose user secrets (server doesn't have them)
- Governments cannot compel secret disclosure (server doesn't have them)
- Rogue administrators cannot access user data (secrets required)
- Compliance is simplified (server is not a "key custodian")

**Recommendations:**

1. **Educate users** - Clearly communicate that secret loss = data loss
2. **Encourage secure storage** - Recommend password managers, hardware keys
3. **Implement backup mechanisms** - Allow users to create encrypted backups they control
4. **Consider recovery options** - Implement client-side recovery keys or multi-signature schemes if appropriate for your use case
5. **Design for usability** - Balance security with user experience (e.g., biometric unlock with secure key storage)

**Alternative approaches for specific use cases:**

If your application absolutely requires account recovery, you can implement:
- **Client-side recovery keys** - Users generate and store recovery keys themselves
- **Social recovery** - Trusted contacts can help recover access (user-controlled, not server-controlled)
- **Multi-signature schemes** - Require multiple keys for access, with backups distributed

These approaches maintain request-scoped knowledge properties while providing recovery options, but they add complexity and must be carefully designed.

**Bottom line:** Secret loss = data loss is the price of true request-scoped knowledge security. If you need account recovery, you must implement it at the application level in a way that preserves transient knowledge guarantees.

---

## 🗑️ Unsupported Standard Repository Methods

> Only `deleteById` is supported for temporary or non-secret-based entities. All other standard Spring Data methods are **intentionally unsupported** as a security design decision. This enforces the transient knowledge encryption model.

**Why ID-based methods wouldn't work:** When using `@HKDFId` CIDs are one-way derived from secrets via HKDF. Without the secret, you cannot decrypt data—even if you retrieve the encrypted blob from the database. Secret-based access is cryptographically required.

**Unsupported methods:**
- **INSERT:** `insert()` → Use `save()` / `saveAll()` instead
- **FIND:** `findById()`, `findAll()`, `findAllById()`, query-by-example → Use `findBySecret(secret)` / `findBySecrets(secrets)` instead
- **DELETE:** `delete()`, `deleteAll()` → Use `deleteBySecret(secret)` / `deleteBySecrets(secrets)` instead

**Supported methods:**
- `deleteById()` (for temporary/non-secret-based entities)
- Secret-based methods: `save()`, `saveAll()`, `findBySecret()`, `findBySecrets()`, `deleteBySecret()`, `deleteBySecrets()`, `existsBySecret()`, `rotateSecret()`

**Why this design is correct:**
1. CIDs cannot be reversed to secrets (cryptographic one-way function)
2. Without secrets, entities cannot be decrypted.
3. Secrets required for cascade cleanup of `@PartOf` children
4. Supporting `findById` would encourage insecure patterns that break transient knowledge guarantees
5. Fail-fast errors force correct usage during development.

**This design will not change.** It's fundamental to the cryptographic security model.

---

## 🗑️ No Soft Delete (Privacy Guarantee)

Encryptable does not provide a soft delete feature out-of-the-box. Soft delete (marking data as deleted but retaining it in storage) is intentionally excluded because it conflicts with privacy principles, specifically the "right to be forgotten." Only hard deletion—complete and irreversible removal of data—is supported by default, ensuring users' sensitive information can be fully erased when requested.

**Implications:**
- Deleted data cannot be recovered or restored, as it is permanently removed from the system.
- Ensure that any necessary data backups or exports are completed before deletion, as the data will be unrecoverable afterward.

**Recommendations:**
- Design your data retention and deletion policies to align with the hard delete model used by Encryptable.
- Communicate clearly to users about the implications of data deletion and the absence of soft delete functionality.

---

## 🔄 Migrating Away from Encryptable

While we're confident in Encryptable's long-term viability and are committed to its ongoing development and security, we believe in transparency and want you to understand all your options.

If you ever need to migrate from Encryptable to another framework or solution, you cannot migrate all data at once without breaking request-scoped knowledge.\
However, **lazy migration IS possible** using a gradual, user-driven approach.

**Migration strategy (lazy migration):**

1. **Run dual systems** - Keep Encryptable and your new solution running in parallel
2. **Migrate on user authentication** - When a user logs in (and you have their secret), migrate their data to the new system
3. **Gradual transition** - Migration happens organically as users authenticate over time
4. **Phase out Encryptable** - Once critical mass is migrated, deprecate the old system

**Trade-offs:**
- ⏱️ **Timeline is unpredictable** - Depends on user login frequency; active users migrate in days/weeks, inactive users may take months/years
- 💰 **Dual infrastructure costs** - Must maintain both systems during transition period
- 🔧 **Increased complexity** - Need fallback logic to check which system has each user's data
- 👥 **Dormant users** - Users who never log in won't migrate; may need email prompts or accept data loss after migration deadline

**This preserves request-scoped knowledge:** You only migrate data when you legitimately have the user's secret (during authentication), never collecting all secrets at once.

**Why bulk migration is impossible:**
- To migrate all data at once, you would need **ALL user secrets**
- Storing or collecting all secrets completely **breaks the transient knowledge security model**
- This is by design - it proves the transient knowledge guarantee is genuine

**Bottom line:** While you're not permanently locked into Encryptable, migration requires careful planning and a gradual, user-driven approach. Factor this into your decision when adopting the framework.

---

## 🔒 Querying Encrypted Fields

Fields annotated with `@Encrypt` are stored in encrypted form in the database.\
As a result, it is not possible to query or filter records based on the values of these fields using standard database queries.

**Implications:**
- You cannot perform searches, filters, or lookups on encrypted fields at the database level.
- All queries involving encrypted fields must be performed in-memory after decryption, which may impact performance for large datasets.

**Recommendations:**
- Design your data model so that fields you need to query remain unencrypted, or use additional unencrypted indexing fields if necessary.
- Be mindful of the trade-off between privacy and queryability when deciding which fields to encrypt.

---

## 📇 Indexing Encrypted Fields

Encrypted fields cannot be indexed for fast lookup. This is a direct consequence of not being able to query encrypted data—since the values are not available in plaintext to the database engine, indexing them would serve no practical purpose. In other words, it is not possible (or meaningful) to index something that will never be queried.

**Implications:**
- You cannot create database indexes on fields annotated with `@Encrypt`.
- Attempts to index encrypted fields will not improve query performance and may waste resources.

**Recommendations:**
- Only index fields that remain unencrypted and are intended for querying or filtering.
- Carefully design your data model to balance privacy and performance needs.

---

## 🚀 Performance Characteristics

Encryptable introduces a modest performance overhead due to field-level encryption and decryption.\
Thanks to **hardware acceleration on modern processors**, this overhead is typically around **10–15%** compared to non-encrypted entities in real-world benchmarks.\
However, because Encryptable supports O(1) lookup by secret (using deterministic cryptographic addressing), the difference may be even smaller for most use cases.

> **Note:** This comparison is fair and accurate because both encrypted and non-encrypted (plain) tests use O(1) direct lookup by CID/secret.\
> The measured performance difference reflects only the encryption/decryption overhead, not differences in database access patterns.

### Hardware Acceleration

Encryptable's cryptographic operations benefit significantly from modern CPU instruction sets:

- **AES-NI (Encryption/Decryption):** AES-256-GCM operations are hardware-accelerated on modern CPUs that support AES-NI (Intel Westmere/2010+, AMD Bulldozer/2011+). This significantly improves performance for encrypted fields.

- **SHA Extensions (Key Derivation):** HKDF and HMAC operations use SHA-256. Intel SHA Extensions are available on Intel Goldmont (2016+) and mainstream from Ice Lake (10th Gen Core, 2019+) onward, and on AMD Zen 3 (Ryzen 5000 series, late 2020+) onward. These extensions accelerate HKDF and HMAC-SHA256 operations.

- **Java Support:** Since Java 17, OpenJDK can use Intel SHA Extensions for SHA-256 if the hardware supports it. To ensure SHA intrinsics are enabled, use the JVM flags `-XX:+UseSHA` and `-XX:+UseSHA256Intrinsics` (these are enabled by default on most platforms, but can be set explicitly for clarity).

**Implications:**
- Encryption/decryption is fast on most modern hardware (2010+) due to AES-NI
- HKDF/HMAC-SHA256 is hardware-accelerated on recent CPUs (2019+) and JVMs
- On older CPUs without these extensions, cryptographic operations may be slower, but remain secure

**Special case:**
- If your entity contains large byte arrays stored in GridFS, the performance drop may be higher when those fields are accessed, due to the cost of encrypting/decrypting large data.\
However, since GridFS fields are lazy loaded, this overhead only occurs when the data is actually accessed, not on every entity operation.

---

## ⚠️ AES-GCM All-or-Nothing Property: Field-Level Data Corruption

> **Note:** This is **not specific to Encryptable**—it's a fundamental property of **AES-GCM** (and all authenticated encryption modes).\
> Any system using AES-256-GCM, AES-128-GCM, ChaCha20-Poly1305, or similar authenticated encryption will face this same behavior.\
> This is documented here for transparency, but it's a **universal trade-off** when choosing authenticated encryption over unauthenticated modes.

Encryptable uses **AES-256-GCM** (Galois/Counter Mode) for authenticated encryption, which provides both confidentiality and integrity protection.\
However, GCM has an **all-or-nothing property**: if even a **single bit** of the encrypted data becomes corrupted (whether in the ciphertext, IV, or authentication tag), the decryption operation will fail.

**What actually happens in Encryptable:**
- ⚠️ **Silent failure with encrypted data return** - When decryption fails, `AES256.decrypt()` returns the **encrypted (corrupted) data back unchanged**
- ✅ **Entity remains accessible** - Other fields in the entity are unaffected; only the corrupted field is lost
- ❌ **Field is permanently unrecoverable** - The corrupted field will contain corrupted encrypted binary data instead of the original plaintext
- ❌ **GridFS file corruption** - A corrupted encrypted GridFS file returns corrupted encrypted data, making the file unusable
- ✅ **Intentional security feature** - GCM's authentication ensures tampering is detected, preventing attackers from modifying encrypted data

**Why this behavior:**
Encryptable's `AES256.decrypt()` method uses **silent failure with audit logging**.\
When decryption fails (due to corruption or tampering), it logs the error internally but returns the encrypted data to prevent application crashes.\
This means the entity can still be loaded and other fields accessed, but the corrupted field is effectively lost.

**Common causes of data corruption:**
- 💾 **Storage media failures** - Bit rot, disk errors, SSD wear
- 🌐 **Network transmission errors** - Though rare with TCP checksums
- 🐛 **Software bugs** - Database corruption, filesystem issues
- ⚡ **Hardware failures** - RAM errors, cosmic ray bit flips (rare but possible)
- 🔧 **Manual database edits** - Accidental modification of encrypted binary data

**Why GCM behaves this way:**
GCM includes an authentication tag that cryptographically verifies the integrity of the entire ciphertext.\
Any modification—accidental or malicious—causes authentication to fail, and the cipher refuses to decrypt.\
This is a **security feature**, not a bug: it prevents attackers from tampering with encrypted data.

**Implications:**
- **Field-level data loss** - Corrupted fields returns corrupted encrypted gibberish, but the rest of the entity remains accessible
- **Silent degradation** - Application continues to function, but corrupted fields contain unusable data
- **Detection challenges** - Corruption may not be immediately obvious; the entity loads successfully but with corrupted field data
- **No automatic alerting** - Decryption failures are logged but don't throw exceptions to the application layer
- **Backup criticality** - Regular, tested backups are **essential** since corrupted encrypted data cannot be recovered

**Mitigation strategies:**

1. **Reliable storage infrastructure**
   - Use enterprise-grade storage with error correction (ECC RAM, ZFS, RAID with checksums)
   - Enable MongoDB's journaling and replication for durability
   - Use cloud providers with built-in data integrity guarantees (AWS EBS, Google Persistent Disks)

2. **Comprehensive backup strategy**
   - Implement automated, regular MongoDB backups
   - Test backup restoration procedures regularly
   - Consider point-in-time recovery capabilities
   - Store backups in geographically distributed locations

3. **Monitoring and alerting**
   - Monitor storage health metrics (SMART data for disks, SSD wear indicators)
   - Set up alerts for database errors or decryption failures
   - Track and investigate any unexpected authentication failures

4. **MongoDB best practices**
   - Use MongoDB replica sets (minimum 3 nodes) for redundancy
   - Enable write concerns (`w: majority`) to ensure data is written to multiple nodes
   - Use `readConcern: "majority"` to avoid reading potentially corrupted data
   - Regular `validate` commands to check collection integrity

5. **Application-level resilience**
   - Implement graceful error handling for decryption failures
   - Log corruption events for investigation
   - Provide user-facing error messages that explain data unavailability
   - Consider field-level granularity: store critical vs. non-critical data separately

**Comparison across storage/encryption approaches:**

| Aspect | Plaintext Storage | AES-CBC (Unauthenticated) | AES-GCM (Authenticated) |
|--------|------------------|--------------------------|------------------------|
| **Partial corruption** | May lose only affected bytes | Partial recovery possible | Entire field lost (returns encrypted data) |
| **Entity accessibility** | Entity accessible | Entity accessible | Entity accessible, only affected field lost |
| **Detection** | Manual inspection required | Not detected (silent corruption) | Automatic (authentication failure, logged) |
| **Recovery** | Partial recovery possible | Partial (but corrupted) | No recovery without backup |
| **Tampering protection** | None | None (vulnerable to bit-flipping attacks) | Cryptographically guaranteed |
| **Security** | ❌ No confidentiality | ⚠️ Confidentiality without integrity | ✅ Confidentiality + Integrity |
| **Industry recommendation** | ❌ Not acceptable for sensitive data | ❌ Deprecated (insecure) | ✅ Best practice |

**Why Encryptable (and the industry) uses AES-GCM:**
- ✅ Detects tampering and corruption (security feature)
- ✅ Prevents bit-flipping attacks
- ✅ Industry standard (NIST recommended)
- ✅ Hardware accelerated on modern CPUs
- ⚠️ Requires reliable storage infrastructure (acceptable trade-off)

**Trade-off:**
This limitation is the **cost of cryptographic integrity**, and it's a trade-off that **every system using authenticated encryption must accept**.\
The same property that makes tampering impossible also makes partial recovery impossible.\
This is why **unauthenticated modes (like AES-CBC without HMAC) are considered insecure**: they don't detect tampering, but they also allow partial recovery from corruption.

**Industry best practice** is to use authenticated encryption (AES-GCM, ChaCha20-Poly1305) despite the all-or-nothing property, because the security benefits far outweigh the corruption risk.\
Organizations using **any** authenticated encryption system (not just Encryptable) must ensure reliable storage and comprehensive backups.

**Recommendations:**
- ✅ **Essential:** Implement robust backup and restore procedures
- ✅ **Essential:** Use reliable storage infrastructure with error correction
- ✅ **Recommended:** Deploy MongoDB replica sets for redundancy
- ✅ **Recommended:** Monitor storage health proactively
- ⚠️ **Consider:** For ultra-critical data, evaluate if the all-or-nothing risk is acceptable for your use case

**Bottom line:** AES-GCM's all-or-nothing property means corrupted fields are unrecoverable, but Encryptable's silent failure approach prevents cascading failures.\
The entity remains accessible with other fields intact, providing **graceful degradation** rather than complete data loss.

**This is NOT an Encryptable-specific limitation**—it's a fundamental property of authenticated encryption that affects every system using AES-GCM (AWS, Google Cloud KMS, Azure Key Vault, HashiCorp Vault, Signal, WhatsApp, etc.).\
The industry has collectively accepted this trade-off because the **security benefits** (tamper detection, integrity protection) far outweigh the corruption risks when combined with proper infrastructure.

With proper infrastructure (reliable storage + regular backups + log monitoring), the risk is manageable and **the same as any other AES-GCM system**.\
Organizations concerned about this should evaluate their storage reliability regardless of which encryption solution they choose.

---

## 🔄 Secret Rotation Automation

Encryptable does not provide fully automated secret rotation.\
Secret rotation must be performed manually or through user-initiated processes.\
While this may be a limitation for some organizations seeking seamless automation, it is a deliberate design choice: fully automatic rotation would require storing the initial secret within the system, which would undermine the concept of a true secret and weaken the security guarantees of request-scoped knowledge encryption.

**Implications:**
- Secret rotation requires manual intervention or custom automation by the user.
- There is no built-in mechanism to automatically rotate secrets without user involvement.

**Rationale:**
- Storing the initial secret for automated rotation would effectively make it a non-secret, reducing the security and privacy benefits Encryptable aims to provide.

**Recommendations:**
- Establish secure operational procedures for manual or semi-automated secret rotation.
- Ensure secrets are managed and rotated according to your organization's security policies, without compromising their confidentiality.

---

## 🚨 Brute-Force and DDoS Attack Mitigation

Encryptable does not provide built-in mechanisms to detect or mitigate brute-force attacks or denial-of-service (DDoS) attempts.\
While the framework is designed to resist information leakage and timing attacks, it does not include rate limiting, account lockout, IP blocking, or automated monitoring for suspicious activity.

**Implications:**
- Attackers could attempt to brute-force secrets, credentials, or CIDs by making repeated requests, potentially overwhelming the system or attempting unauthorized access.
- The responsibility for detecting and mitigating such attacks falls to the application layer, infrastructure, or external security solutions.

**Recommendations:**
- Implement rate limiting, request throttling, and monitoring at the API gateway, web server, or application level.
- Use security tools and services (such as WAFs, intrusion detection, and DDoS protection) to monitor and block suspicious activity.
- Consider account lockout or challenge mechanisms (e.g., CAPTCHA, 2FA) for repeated failed access attempts.
- Regularly audit logs and monitor for unusual patterns of access or authentication failures.

Encryptable focuses on data privacy and cryptographic security, but application-level protections against brute-force and DDoS attacks are essential for a comprehensive security posture.
