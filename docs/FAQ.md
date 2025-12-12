# ❓ Frequently Asked Questions (FAQ)

## 🌟 General Questions

### 🏗️ Why is Encryptable called a "Framework" instead of a "Library"?

**Framework vs. Library:** Libraries provide functions you call; frameworks call your code and manage the lifecycle.

**Encryptable is a framework because it:**

1. **Requires Architectural Planning**
   - **Developers must plan their implementation around Encryptable, not add Encryptable to existing implementation**
   - Entities must be designed with cryptographic addressing in mind
   - Data modeling requires understanding CID generation and zero-knowledge principles
   - Application architecture must account for secret management and lifecycle
   - This is the defining characteristic: **you build around the framework, not with it as an add-on**

2. **Controls Application Architecture**
   - Enforces entity design patterns (all entities extend `Encryptable<T>`)
   - Dictates data modeling approach (cryptographic addressing)
   - Mandates repository structure (`EncryptableMongoRepository<T>`)

3. **Manages Entire Request Lifecycle**
   - Intercepts method calls via AspectJ weaving (AOP)
   - Automatically encrypts/decrypts data transparently
   - Manages memory hygiene per-request (ThreadLocal cleanup)
   - Controls when and how data is persisted

4. **Inversion of Control**
   - YOU don't call encryption/decryption - the framework does
   - YOU don't manage secrets - the framework does
   - YOU don't handle GridFS files - the framework does

5. **Convention Over Configuration**
   - `@EnableEncryptable` bootstraps entire subsystem
   - Auto-configuration for MongoDB, AspectJ, repositories
   - Opinionated defaults (AES-256-GCM, HKDF, thread-local isolation)

6. **Provides Complete Data Layer**
   - Custom repository implementations (`EncryptableMongoRepository`)
   - Custom entity lifecycle (prepare, restore, touch)
   - Custom ID generation strategy (CID via HKDF)
   - Relationship management and cascade operations

**Comparison:**

| Library/Extension | Framework (Encryptable) |
|-------------------|-------------------------|
| Add-on to existing code | Structural foundation |
| Encryption utility | Complete data security layer |
| Manual integration | Auto-configuration |
| Explicit method calls | Transparent interception |

**Example showing framework control:**

```kotlin
// YOU don't call encryption - the framework does it automatically
@Encrypt
var email: String? = null

// YOU don't manage the lifecycle - the framework intercepts and controls it
val user = userRepository.save(user) // Framework encrypts before save

// YOU don't load GridFS files - the framework loads them transparently
val photo = user.profilePhoto // Framework intercepts getter and loads from GridFS

// YOU don't clean memory - the framework does it at request end
```

**Result:** Encryptable doesn't just provide crypto functions - it fundamentally changes how you build data layers, manages your application's cryptographic lifecycle, and enforces zero-knowledge architecture through inversion of control. That's a framework.

---

### 🏗️ Is Encryptable a "framework built on top of frameworks"?

**Yes, exactly.** Encryptable is a framework built on top of Spring Boot and Spring Data MongoDB.

**Layered architecture:**
```
┌─────────────────────────────────────┐
│   Your Application Code             │  ← Your business logic
├─────────────────────────────────────┤
│   Encryptable Framework             │  ← Zero-knowledge, crypto addressing, ORM
├─────────────────────────────────────┤
│   Spring Data MongoDB               │  ← Repository abstraction, MongoDB integration
├─────────────────────────────────────┤
│   Spring Boot / Spring Framework    │  ← DI, AOP, lifecycle management
├─────────────────────────────────────┤
│   MongoDB Driver                    │  ← Database communication
└─────────────────────────────────────┘
```

**This is a strength, not a weakness:**

1. **Stands on Giants' Shoulders**
   - Spring Boot: Battle-tested DI, AOP, lifecycle management (20+ years of development)
   - Spring Data MongoDB: Proven repository patterns, connection pooling, transaction support
   - Encryptable adds: Zero-knowledge architecture, cryptographic addressing, memory hygiene

2. **Framework Composition is Standard Practice**
   - Spring Security is built on Spring Framework
   - Spring Data JPA is built on Spring Framework + Hibernate
   - Next.js is built on React
   - **Encryptable is built on Spring + Spring Data MongoDB**

3. **Extends, Not Replaces**
   - You still use Spring's `@Autowired`, `@Service`, `@Configuration`
   - You still use Spring Data's repository patterns
   - Encryptable extends these with `Encryptable` base class, `@Encrypt`, `@HKDFId`, `EncryptableMongoRepository`

4. **Framework-Level Integration**
   - Encryptable integrates deeply with Spring's lifecycle (AspectJ weaving, bean management)
   - Extends Spring Data's repository layer with custom implementations
   - Manages its own request-scoped context via ThreadLocal (parallel to Spring's request context)

**Why this matters:**

Encryptable is not just "a library you add to Spring" - it's a **specialized framework** that requires architectural planning and changes how you design your data layer, built on top of proven infrastructure (Spring/Spring Data MongoDB).

**Analogy:**
- **Library:** Adding encryption utility functions to your Spring app
- **Framework:** Encryptable fundamentally changes how you model entities, manage secrets, and interact with MongoDB - all while leveraging Spring's infrastructure

---

## 🔒 Security Questions

### 🛡️ Has Encryptable undergone a professional security audit?

No, not yet. Encryptable has undergone AI-assisted security analysis but has not yet received a professional cryptographic audit from a certified firm.

**Current status:**
- ✅ AI-assisted security analysis completed ([AI Security Audit](AI_SECURITY_AUDIT.md))
- ✅ Based on proven cryptographic primitives (AES-256-GCM, HKDF/RFC 5869)
- ✅ Transparent about limitations and constraints
- ❌ No professional audit from certified security firm

**Recommended usage:**
- ✅ Personal projects and hobby applications
- ✅ Startups and MVPs
- ✅ General web applications
- ⚠️ Requires professional audit for: Healthcare (HIPAA), Finance (PCI-DSS), Government/Defense

See [Limitations](LIMITATIONS.md) and [Security Without Audit](SECURITY_WITHOUT_AUDIT.md) for details.

### 🔑 What happens if I lose my secret?

**The data is lost forever.** This is by design.

Encryptable implements true zero-knowledge architecture, which means:
- The server cannot reconstruct your secret
- The server cannot reset your password
- The server cannot access your data without your secret
- No "forgot password" recovery is possible

This is a feature, not a bug. True zero-knowledge means the server has **zero** knowledge of your data.

**Best practices:**
- Use secure secret storage (password managers, hardware keys)
- Implement client-side secret backup mechanisms if needed
- Make users aware that lost secrets cannot be recovered
- Consider multi-factor approaches for high-value accounts

See [Security Without Secret](SECURITY_WITHOUT_SECRET.md) for strategies.

### 🔍 Why can't I query encrypted fields?

Encrypted data is randomized ciphertext - it cannot be indexed or queried directly by the database.

**Technical reason:** AES-256-GCM produces different ciphertext for the same plaintext each time due to random IVs (Initialization Vectors). This is essential for security but prevents database queries.

**This is by design and cannot be "worked around"** - true zero-knowledge architecture means:
- The server cannot search encrypted data (it doesn't have the secret)
- The database cannot index ciphertext (it's randomized per encryption)
- Only the user with the secret can decrypt and search their own data

**Alternative approaches:**
1. **Cryptographic addressing** - Use a deterministic CIDs for direct O(1) entity lookups by secret (no search needed)
2. **Metadata fields** - Store non-sensitive searchable metadata separately (e.g., `@Encrypt var email` + `var emailDomain: String` for searching by domain)
3. **Client-side filtering** - After fetching and decrypting entities with the user's secret, filter in application code (only works for data the user owns)

**Key point:** If you need to search encrypted fields across multiple users' data (e.g., admin searching all emails), Encryptable's zero-knowledge architecture is not suitable for that use case. Zero-knowledge means zero ability to search without the secret.

See [Limitations](LIMITATIONS.md) for more details.

---

## 🏛️ Architecture Questions

### 🔐 What is cryptographic addressing?

Cryptographic addressing is a novel pattern where entity identifiers (CIDs) are **deterministically derived from user secrets** using HKDF (HMAC-based Key Derivation Function).

**Traditional approach:**
```
User creates account → Generate random ID → Store mapping (username → ID)
User logs in → Look up username → Find ID → Query database
Result: Two database queries + mapping table maintenance
```

**Cryptographic addressing:**
```
User creates account → Derive CID from secret using HKDF → Store entity
User logs in → Derive CID from secret using HKDF → Query database directly
Result: One database query + zero mapping tables
```

**Benefits:**
- ✅ O(1) direct database access (no username lookups)
- ✅ Zero mapping tables (no username→ID storage)
- ✅ True zero-knowledge (server can't enumerate users)
- ✅ Stateless operations (CID computed on-demand)
- ✅ Privacy by design (no user enumeration possible)

See [Cryptographic Addressing](CRYPTOGRAPHIC_ADDRESSING.md) for technical details.

### 🗄️ Can I use Encryptable with existing databases?

It's possible but complex. Encryptable is designed for **new systems** where you can build the data model around cryptographic addressing from the start.

**Challenges for migration:**
- Existing IDs (UUIDs, auto-increment) need conversion to CIDs
- Existing relationships need restructuring
- User authentication flows need complete redesign
- No username/email storage means fundamental schema changes

**Recommendation:** Use Encryptable for new projects or new modules within existing systems, rather than full migrations.

See [Limitations](LIMITATIONS.md) for migration considerations.

### ⚡ Why doesn't Encryptable support Kotlin coroutines?

Encryptable uses **ThreadLocal** for per-request memory hygiene and secret isolation. Kotlin coroutines can suspend and resume on different threads, which breaks ThreadLocal guarantees.

**Technical incompatibility:**
- ThreadLocal binds data to a specific OS thread
- Coroutines can migrate between threads during suspension
- Secrets stored in ThreadLocal could be lost or accessed by wrong request

**Workaround:** Use Spring Boot's traditional thread-per-request model (supported out of the box).

See [Coroutines Incompatibility](COROUTINES_INCOMPATIBILITY.md) for detailed analysis.

---

## ⚡ Performance Questions

### 🚀 How does Encryptable impact database performance?

**Positive impact:** Cryptographic addressing dramatically improves database performance by eliminating index scans.

**Traditional systems:**
```
Client → DB searches username index (O(log n))
       → DB compares password hash (expensive CPU)
       → DB returns user
= Database CPU saturated with index scans
```

**Encryptable:**
```
Client derives CID (client-side, milliseconds)
      → DB performs direct _id lookup (O(1), instant)
      → DB returns encrypted record
= Database freed for other requests
```

**Benefits:**
- 5-10x increase in database throughput
- No index maintenance overhead
- Lower CPU/RAM/disk usage per request
- Primary key access only (peak efficiency)

See [Innovations](INNOVATIONS.md) for performance analysis.

### 🔐 Does encryption slow down my application?

Minimal impact. Encryption/decryption happens transparently via AspectJ, and modern CPUs have hardware acceleration for AES.

**Performance characteristics:**
- AES-256-GCM: ~1-2 GB/sec on modern CPUs (hardware-accelerated)
- HKDF derivation: ~1-2 milliseconds per operation
- GridFS files: Encrypted on upload, decrypted on-demand (lazy loading)

**Optimization:** Encryptable parallelizes encryption/decryption for multiple fields, leveraging multi-core CPUs.

---

## 💻 Usage Questions

### ☕ Can I use Encryptable with Java?

Yes, but with limitations. Encryptable is **Kotlin-first** by design.

**What works in Java:**
- ✅ Basic entity definitions
- ✅ Repository operations
- ✅ Encryption/decryption (automatic)
- ✅ Relationship management

**What's limited in Java:**
- ❌ Extension functions (Kotlin-only feature)
- ❌ DSL features and builder patterns
- ❌ Idiomatic Kotlin syntax (data classes, etc.)

**Recommendation:** Use Kotlin for the best experience. The framework's API is designed around Kotlin's language features.

### 🍃 What MongoDB versions are supported?

Encryptable works with **MongoDB 4.0+** via Spring Data MongoDB.

**Requirements:**
- MongoDB 4.0 or higher
- GridFS support (for file encryption)
- Standard MongoDB operations (no special features required)

**Cloud compatibility:**
- ✅ MongoDB Atlas
- ✅ Self-hosted MongoDB
- ✅ Docker containers
- ✅ Kubernetes deployments

### 🔧 Can I customize the encryption algorithm?

No. Encryptable uses **AES-256-GCM** exclusively.

**Why fixed:**
- AES-256-GCM is industry-standard (NSA-approved for TOP SECRET data)
- Considered unbreakable with current technology
- Hardware-accelerated on modern CPUs
- Provides both confidentiality and integrity (AEAD)

**Opinionated design:** Encryptable makes secure-by-default choices so developers don't need to be cryptography experts.\
Algorithm customization could introduce security risks.

### 🔗 Why not combine multiple algorithms like VeraCrypt?

**Because AES-256-GCM is enough.**

VeraCrypt (successor to TrueCrypt) and similar tools offer cascading encryption (AES + Serpent + Twofish) as a hedge against potential cryptographic breakthroughs.\
However, for Encryptable's use case, this adds unnecessary complexity without meaningful security benefits.

**Why single-algorithm (AES-256-GCM) is the right choice:**

1. **AES-256 is sufficient**
   - Approved for TOP SECRET data by NSA
   - No practical attacks exist against AES-256
   - If AES-256 is broken, we have bigger problems (entire internet security collapses)

2. **Performance cost**
   - Multiple algorithms = 2-3x slower encryption/decryption
   - No hardware acceleration for cascaded algorithms
   - Database operations would be significantly slower

3. **Complexity = risk**
   - More code = more potential bugs
   - Harder to audit and verify
   - Implementation errors more likely than cryptographic breakthroughs

4. **GCM provides authenticated encryption**
   - AES-256-GCM provides both confidentiality AND integrity
   - Single algorithm doing two jobs efficiently
   - No need for separate HMAC or authentication layer

5. **Industry standard**
   - Signal Protocol: AES-256-GCM
   - TLS 1.3: AES-256-GCM preferred
   - Cloud providers (AWS, Azure, Google): AES-256-GCM default
   - If it's good enough for them, it's good enough for us

**The pragmatic view:**
- If AES-256 gets broken, cascading won't save you (the NSA will have bigger targets)
- Performance and simplicity matter for production systems

**Bottom line:** Encryptable prioritizes practical security over theoretical paranoia.\
AES-256-GCM is battle-tested, hardware-accelerated, and trusted by governments worldwide.
---

## 🔧 Troubleshooting

### 🔓 My encrypted fields are not being decrypted

Common causes:
1. **Secret not provided to repository method** - Repository methods like `findBySecretOrNull(secret)` automatically call `restore(secret)` internally
2. **Wrong secret provided** - Secret must match the one used during encryption

**Note:** You don't need to manually call `entity.restore(secret)` - the `EncryptableMongoRepository` handles this automatically when you use methods like `findBySecretOrNull(secret)`.

### 📁 GridFS files are not loading

GridFS files are loaded **lazily** via AspectJ field interception. Make sure:
1. AspectJ weaving is configured correctly
2. You're accessing the field (not just the entity)
3. The GridFS template is properly configured in Spring

---

## 📜 Licensing & Support

### 💰 Is Encryptable really free?

**Yes, completely free and open-source under the MIT License.**

- ✅ Use in commercial projects (no fees)
- ✅ Modify and distribute (with attribution)
- ✅ Private use (no restrictions)
- ✅ Patent grant included (MIT License)

**No hidden costs, no "enterprise edition", no restrictions.**

### 🤝 How can I support the project?

- **Sponsor the author:** [GitHub Sponsors](https://github.com/sponsors/WanionCane)
- **Contribute code:** See [Contributing](../CONTRIBUTING.md)
- **Report bugs:** Open [GitHub Issues](https://github.com/WanionTechnologies/Encryptable/issues)
- **Spread the word:** Write blog posts, give talks, share on social media

See [Sponsorship Goals](SPONSORSHIP_GOALS.md) for funding plans (professional audit, full-time development).

---

## 📚 See Also

- [Innovations](INNOVATIONS.md) - Technical innovations and novel contributions
- [Limitations](LIMITATIONS.md) - Known constraints and trade-offs
- [Prerequisites](PREREQUISITES.md) - System requirements and setup
- [Configuration](CONFIGURATION.md) - Framework configuration options
- [Best Practices](BEST_PRACTICES.md) - Secure memory handling and usage patterns

