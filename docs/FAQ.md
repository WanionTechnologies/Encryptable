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
   - Intercepts field access via AspectJ weaving (AOP)
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

### ❗ Why isn't Encryptable Zero-Knowledge?

Encryptable is not a strict zero-knowledge system. While it achieves request-scoped (transient) knowledge—meaning secrets and keys are only present in memory for the duration of a request or operation—there are brief periods where the server does have access to user secrets in memory. This is a necessary trade-off for practical backend-only applications, and Encryptable is designed to minimize this exposure as much as possible.

We apologize for previously stating that Encryptable was zero-knowledge. Our documentation and messaging have been updated to clarify that, while Encryptable is not zero-knowledge in the strict cryptographic sense, it is as close as possible for backend-only architectures. For true zero-knowledge, secrets must never be present on the server at any time, which is only achievable with client-side cryptography.

For full details and limitations, see [Limitations](LIMITATIONS.md).

---

### ❓ Common Misconceptions About Encryptable

#### **"This is just encrypting data before storing it in the database"**

❌ **Misconception:** Encryptable simply encrypts fields before database insertion.

✅ **Reality:** Encryptable provides a complete architecture including:
- **Cryptographic addressing** - IDs derived from secrets (HKDF)
- **Per-entity isolation** - Each entity encrypted with different keys
- **Anonymous data model** - No user identities stored
- **Request-scoped secrets** - Zero persistent knowledge
- **ORM features** - Relationships, polymorphism, cascade delete
- **Automatic change detection** - Field-level hashing
- **GridFS integration** - Encrypted large file handling

**Database "encryption at rest" protects against disk theft.**  
**Encryptable protects against insider threats, admin access, and server compromise.**

#### **"Database encryption at rest already exists, this doesn't add value"**

❌ **Misconception:** Encryptable duplicates existing database encryption features.

✅ **Reality: Completely different threat models:**

| Threat | DB Encryption at Rest | Encryptable |
|--------|----------------------|-------------|
| Stolen hard drive | ✅ Protected | ✅ Protected |
| Database admin snooping | ❌ Exposed | ✅ Protected |
| Server memory dump | ❌ Exposed | ⚠️ Protected* |
| Insider threats | ❌ Exposed | ✅ Protected |
| Credential database leak | ❌ Exposed | ✅ Protected** |
| Password reset by admin | ❌ Possible | ✅ Impossible |

*Protected outside request scope (transient knowledge)  
**When using @HKDFId with anonymous data model

**Encryptable and database encryption solve different problems and are complementary, not alternatives.**

#### **"You can build this in a few hours, it's not innovative"**

❌ **Misconception:** Encryptable is trivial and easy to replicate.

✅ **Reality:** Try implementing these features in "a few hours":

1. **HKDF-based deterministic ID generation** with automatic class namespacing
2. **Automatic entropy validation** (Shannon entropy + repetition checking)
3. **Per-entity cryptographic isolation** without key storage
4. **Request-scoped secret lifecycle** with automatic memory wiping
5. **AspectJ-based transparent encryption/decryption** of nested fields
6. **Polymorphic relationship support** with automatic type preservation
7. **Cascade delete** across encrypted references
8. **GridFS integration** with automatic encryption/decryption and lazy loading
9. **Automatic change detection** via field hashing
10. **2700+ lines of production code** with 81 passing tests

**If it's so easy, where are the alternatives?**

#### **"For true security, you need client-side encryption"**

❌ **Misconception:** Backend encryption is fundamentally insecure.

✅ **Reality:** Different use cases require different architectures:

**Client-side encryption (Signal, ProtonMail):**
- ✅ Server never sees plaintext
- ❌ Cannot perform server-side business logic
- ❌ Cannot query or process encrypted data
- ❌ Requires client implementation
- **Use case:** End-to-end encrypted messaging

**Backend encryption with request-scoped secrets (Encryptable):**
- ⚠️ Server sees plaintext during request only
- ✅ Full server-side business logic capability
- ✅ Relationships, validation, complex operations
- ✅ Works with any client (web, mobile, API)
- **Use case:** Privacy-focused backend applications

**Neither approach is "better" - they solve different problems.**

Encryptable enables backend-only applications to achieve strong privacy guarantees without requiring client-side cryptography.

#### **"This is just SSE-C (Server-Side Encryption with Customer Keys)"**

❌ **Misconception:** Encryptable is equivalent to SSE-C.

✅ **Reality:** SSE-C is a storage encryption pattern. Encryptable is a complete framework.

**SSE-C provides:**
- Encryption of data at rest
- Customer-provided encryption keys
- Key management responsibility on client

**Encryptable additionally provides:**
- Cryptographic addressing (HKDF-based IDs)
- Anonymous data model (no user storage)
- ORM features (relationships, polymorphism, cascade delete)
- Request-scoped secret lifecycle
- Automatic change detection
- GridFS integration
- Framework-managed encryption/decryption

**SSE-C is a storage pattern. Encryptable is a data persistence framework with privacy-first architecture.**

---

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

---

### 🤖 Was AI used in developing Encryptable?

**Yes, for documentation and repetitive tasks. No, for core features and algorithms.**

**Full transparency on what was human-created vs AI-assisted:**

#### ✅ **100% Human-Created by WanionCane:**

1. **All Core Architecture & Algorithms**
   - Cryptographic addressing concept and implementation
   - HKDF-based CID derivation logic
   - AES-256-GCM encryption/decryption flow
   - Request-scoped secret lifecycle management
   - Memory hygiene strategy (ThreadLocal, zerification)
   - GridFS integration architecture
   - Polymorphism resolution system
   - Relationship management (One-to-One, One-to-Many, Many-to-Many)
   - Cascade delete implementation
   - Change detection via field hashing
   - AspectJ weaving strategy

2. **All Production Code**
   - 2700+ lines of Kotlin implementation
   - Entity base classes (`Encryptable<T>`)
   - Repository implementations (`EncryptableMongoRepository`)
   - Cryptographic utilities (HKDF, AES, CID)
   - AspectJ aspects for field interception
   - Spring Boot auto-configuration
   - All business logic and security decisions

3. **Framework Design Decisions**
   - Security model (transient knowledge)
   - API design and developer experience
   - Annotation strategy (`@Encrypt`, `@HKDFId`, `@PartOf`)
   - Trade-off decisions (CID requirement, no coroutines support, etc.)

#### 🤖 **AI-Assisted (GitHub Copilot):**

1. **Documentation Writing**
   - README structure and content
   - FAQ answers and formatting
   - Technical guides (INNOVATIONS.md, BEST_PRACTICES.md, etc.)
   - Concept explanations and analogies
   - This FAQ entry you're reading right now

2. **Code Documentation**
   - KDoc comments for classes and methods
   - Parameter descriptions
   - Return value documentation
   - Usage examples in comments

3. **Repetitive Tasks**
   - Formatting and structure consistency
   - Markdown table generation
   - Copy-editing and grammar improvements
   - Reorganizing existing documentation

4. **Content Polishing**
   - Professional tone and clarity
   - Comprehensive coverage of topics
   - Cross-referencing between documents

5. **Test Generation**
   - 81 unit and integration tests
   - Test entities and repositories
   - Test scenarios and edge cases
   - Test data generation and assertions
   - Test structure and organization

#### 🎯 **The Bottom Line:**

**The innovation is 100% human.** AI helped explain it better.

#### 📊 **Why This Transparency Matters:**

1. **Honesty** - We believe in full transparency about AI usage
2. **Credit** - The innovation belongs to WanionCane, not AI
3. **Trust** - You deserve to know what you're evaluating
4. **Community Standards** - Some platforms (Reddit) have strict AI content policies
5. **Language Barrier** - WanionCane is not a native English speaker, so AI assistance was essential for creating clear, comprehensive documentation that the community could understand

#### ⚖️ **Is AI-Assisted Documentation a Problem?**

**It depends on context:**

❌ **Problem when:**
- AI generates code you don't understand
- AI makes architectural decisions
- AI writes algorithms without human verification
- Used to spam or create low-quality content

✅ **Not a problem when:**
- AI helps document human-created work
- AI improves clarity and organization
- AI handles repetitive formatting tasks
- Used to make legitimate work more accessible

**Encryptable falls in the latter category** - AI made the documentation more comprehensive, but didn't create the framework or its innovations.

#### 🛡️ **Verification:**

The code speaks for itself:
- ✅ 2700+ lines of production code (view on GitHub)
- ✅ 81 passing tests verify the code works (even though tests were AI-generated)
- ✅ Novel architecture (compare to alternatives)
- ✅ Consistent commit history (months of development)
- ✅ Technical depth (not surface-level AI generation)

**You can verify that this is real, working, innovative software - not AI-generated vaporware.**

**Note on tests:** While the tests themselves were AI-generated, they verify that human-written production code functions correctly. Think of it as AI helping with QA, not replacing human engineering.

---

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

### 🆚 How does Encryptable compare to vanilla Spring Data MongoDB?

**Encryptable is a superset of Spring Data MongoDB** - it can do (almost) everything vanilla Spring Data MongoDB can, plus additional features.

**Key differences:**

| Feature | Spring Data MongoDB | Encryptable |
|---------|---------------------|-------------|
| ID Type | Any type (String, Long, etc.) | **Must be CID** (hard limitation) |
| Field Encryption | ❌ Manual implementation | ✅ Built-in (`@Encrypt`) |
| Change Detection | ❌ None (full document update) | ✅ Automatic (hash-based) |
| Partial Updates | ⚠️ Manual with MongoTemplate | ✅ Automatic |
| Polymorphism | ⚠️ Requires `@TypeAlias` | ✅ Transparent (zero-config) |
| Cascade Delete | ❌ Manual | ✅ `@PartOf` annotation |
| GridFS | ⚠️ Manual management | ✅ Automatic + encrypted |
| Cryptographic Addressing | ❌ | ✅ Optional (`@HKDFId`) |

**Migration requirement:** 
- You must change ID types from `String` to `CID`
- This is the **only hard limitation** compared to vanilla Spring Data MongoDB

**Worth it?** Yes, if you need:
- Built-in encryption
- Better ORM features (cascade delete, polymorphism)
- Automatic change detection and partial updates
- Security-first architecture

---

### 🆔 Why must IDs be CID type instead of String?

**This is a fundamental architectural decision.**

**Reasons:**

1. **Consistency across entities**
   - Both `@Id` (direct) and `@HKDFId` (derived) strategies use CID
   - Uniform 22-character Base64 URL-safe format
   - Simplifies framework internals

2. **Cryptographic addressing support**
   - CID enables HKDF-based ID derivation from secrets
   - Cannot support arbitrary String types for cryptographic operations
   - 128-bit (16-byte) entropy requirement for secure derivation

3. **URL-safe and compact**
   - CID: 22 characters (Base64 URL-safe, no padding)
   - MongoDB ObjectId: 24 characters (hex)
   - UUID: 36 characters (with dashes)
   - More compact = better for URLs, APIs, logs

**Migration path:**
```kotlin
// Convert existing String IDs to CID
val stringId = "507f1f77bcf86cd799439011"
val cid: CID = stringId.cid  // Extension function

// Or generate new CIDs
val newCid: CID = CID.random()
```

**Trade-off:** You lose ID type flexibility but gain cryptographic addressing, URL-safe identifiers, and framework consistency.

---

### 🧪 How do I test entities with Encryptable?

**Testing is straightforward - Encryptable works with standard Spring Boot testing.**

**Example test:**
```kotlin
@SpringBootTest
class UserRepositoryTest {
    
    @Autowired
    private lateinit var userRepository: UserRepository
    
    @Test
    fun `should save and retrieve encrypted user data`() {
        // Given
        val secret = generateSecret()  // Or use fixed test secret
        val user = User().withSecret(secret).apply {
            email = "test@example.com"
            name = "Test User"
        }
        
        // When
        userRepository.save(user)
        val retrieved = userRepository.findBySecretOrNull(secret)
        
        // Then
        assertNotNull(retrieved)
        assertEquals("test@example.com", retrieved?.email)
        assertEquals("Test User", retrieved?.name)
        
        // Cleanup
        userRepository.deleteBySecret(secret)
    }
}
```

**Best practices:**
- Use fixed secrets for reproducible tests
- Test with actual MongoDB (not in-memory - GridFS requires real MongoDB)
- Use `@DirtiesContext` if sharing test database
- Test both encryption and decryption paths
- Verify cascade delete behavior

**Test containers:**
```kotlin
@Testcontainers
@SpringBootTest
class UserRepositoryIntegrationTest {
    
    companion object {
        @Container
        val mongoContainer = MongoDBContainer("mongo:7.0")
            .apply { start() }
    }
    
    @DynamicPropertySource
    fun mongoProperties(registry: DynamicPropertyRegistry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getReplicaSetUrl)
    }
    
    // ...tests
}
```

---

### 🚀 What are the deployment considerations?

**Encryptable requires specific JVM arguments for reflection access:**

```bash
java --add-opens java.base/javax.crypto.spec=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     -jar your-app.jar
```

**Why required:**
- `javax.crypto.spec`: Encryptable uses `String.zerify()` to securely wipe secrets from memory
- `java.lang`: Required for String manipulation and security features
- Java Module System restrictions require explicit opens

**Deployment checklist:**
- ✅ Add JVM arguments to startup scripts
- ✅ Configure MongoDB connection (supports Atlas, self-hosted, Docker)
- ✅ Ensure MongoDB 4.0+ with GridFS support
- ✅ Enable virtual threads (optional): `spring.threads.virtual.enabled=true`
- ✅ Configure AspectJ compile-time weaving (already in build config)
- ✅ Set master secret if using `@Id` strategy (optional)

**Docker example:**
```dockerfile
FROM eclipse-temurin:21-jre
COPY target/app.jar /app.jar
ENTRYPOINT ["java", \
    "--add-opens", "java.base/javax.crypto.spec=ALL-UNNAMED", \
    "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
    "-jar", "/app.jar"]
```

See [Prerequisites](PREREQUISITES.md) for complete setup.

---

### 🔄 Can I mix @Id and @HKDFId in the same application?

**Yes, absolutely!** This is the recommended pattern for hybrid architectures.

**Example - E-commerce application:**

```kotlin
// Public product catalog - uses @Id (no encryption needed)
@Document(collection = "products")
class Product : Encryptable<Product>() {
    @Id
    override var id: CID? = null
    
    var name: String? = null
    var price: Double? = null
    var category: String? = null
    // No @Encrypt - fully searchable
}

// User data - uses @HKDFId (maximum security)
@Document(collection = "users")
class User : Encryptable<User>() {
    @HKDFId
    override var id: CID? = null
    
    var username: String? = null  // Searchable
    @Encrypt var email: String? = null  // Encrypted
    @Encrypt var address: Address? = null  // Encrypted
}

// Orders - uses @HKDFId with encrypted details
@Document(collection = "orders")
class Order : Encryptable<Order>() {
    @HKDFId
    override var id: CID? = null
    
    var orderNumber: String? = null  // Searchable (for admin)
    var status: OrderStatus? = null   // Searchable
    @Encrypt var items: List<OrderItem> = listOf()  // Encrypted
    @Encrypt var totalAmount: Double? = null  // Encrypted
}
```

**Pattern:**
- **Public data** (products, categories): `@Id` + no encryption
- **User PII** (email, address): `@HKDFId` + `@Encrypt`
- **Transactional data** (orders): Mix of public (status) and encrypted (details)

**Benefits:**
- ✅ Product catalog fully searchable (traditional Spring Data queries)
- ✅ User PII maximally protected (transient knowledge)
- ✅ Admin can query order status, but not see financial details
- ✅ Best of both worlds: flexibility + security

---

### 🌐 Can I use Encryptable in a microservices architecture?

**Yes, Encryptable works perfectly in microservices.**

**Architecture patterns:**

**1. Service-specific secrets:**
```kotlin
// User Service
class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
    // Each user has their own secret
}

// Order Service
class Order : Encryptable<Order>() {
    @HKDFId override var id: CID? = null
    @Encrypt var items: List<OrderItem> = listOf()
    // Each order has its own secret
}
```

**2. Shared secrets (cross-service):**
```kotlin
// User Service - stores secret
val userSecret = generateSecret()
val user = User().withSecret(userSecret)
userRepository.save(user)

// Pass secret to Order Service (via secure API)
val response = orderServiceClient.createOrder(
    userSecret = userSecret,
    items = cartItems
)

// Order Service - uses same secret
class Order : Encryptable<Order>() {
    @HKDFId override var id: CID? = null
    var userId: String? = null  // Reference to user (not encrypted)
    @Encrypt var shippingAddress: Address? = null
}
```

**3. Event-driven architecture:**
```kotlin
// Publish event with secret
eventPublisher.publish(OrderCreatedEvent(
    orderId = order.id,
    userSecret = userSecret,  // Secure channel!
    orderDetails = encryptedData
))

// Consumer uses secret to decrypt
@EventListener
fun handleOrderCreated(event: OrderCreatedEvent) {
    val order = orderRepository.findBySecretOrNull(event.userSecret)
    // Process order...
}
```

**Security considerations:**
- ✅ Each service has its own MongoDB database
- ✅ Secrets transmitted over secure channels (HTTPS, message encryption)
- ✅ Consider secret rotation strategies
- ⚠️ Avoid storing secrets in service databases (use secure vaults)
- ⚠️ API Gateway handles secret validation/routing

---

### 💾 How much storage overhead does encryption add?

**Minimal storage overhead - approximately 5-10% for typical use cases.**

**Breakdown:**

**1. Encrypted fields:**
- AES-256-GCM adds 28 bytes per field:
  - 12-byte IV (Initialization Vector)
  - 16-byte authentication tag
- Base64 encoding: ~33% size increase

**Example:**
```kotlin
@Encrypt var email: String? = "john@example.com"  // 16 bytes plaintext

// Encrypted + Base64:
// 16 (plaintext) + 28 (GCM overhead) = 44 bytes raw
// 44 * 1.33 (Base64) = ~59 bytes stored
// Overhead: 43 bytes (268% for short strings)
```

**2. Metadata overhead:**
```kotlin
class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
    @PartOf var orders: List<Order> = listOf()
}

// Additional fields stored:
// - encryptableListFieldMap: {"orders": ["secret1", "secret2"]}
// - encryptableFieldTypeMap: {} (only if polymorphic)
// Typical overhead: 50-200 bytes per entity
```

**3. GridFS files (>1KB):**
- Negligible overhead (28 bytes per file regardless of size)
- 10 MB file → 10 MB + 28 bytes (~0.0003% overhead)

**Real-world impact:**

| Entity Size | Plaintext | Encrypted | Overhead |
|-------------|-----------|-----------|----------|
| Small (5 fields, 100 bytes) | 100 B | 350 B | 250% |
| Medium (20 fields, 1 KB) | 1 KB | 1.5 KB | 50% |
| Large (50 fields, 10 KB) | 10 KB | 11 KB | 10% |
| With files (10 MB) | 10 MB | 10 MB | ~0% |

**Optimization tips:**
- Short strings have high overhead - consider combining fields
- Large files have negligible overhead - encryption is "free"
- Non-sensitive fields don't need `@Encrypt` (0% overhead)

**Verdict:** Storage overhead is not a concern for most applications. Security benefits far outweigh minimal storage costs.

---

### 🔐 How do I implement "forgot password" if secrets can't be recovered?

**You can't - and that's the point of transient knowledge architecture.**

However, you have several alternative approaches:

**1. Account recovery with backup secrets:**
```kotlin
class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
    @Encrypt var backupCodes: List<String> = listOf()  // One-time recovery codes
}

// User downloads recovery codes at signup
val recoveryCodes = generateRecoveryCodes(count = 10)
user.backupCodes = recoveryCodes
// User stores codes offline (print, password manager)

// Recovery flow
fun recoverAccount(recoveryCode: String): User? {
    // Must iterate all users (expensive!) or use separate mapping
    // This is why transient knowledge doesn't work well with password resets
}
```

**2. Multi-factor with recovery key:**
```kotlin
// Encrypt user secret with recovery key
val userSecret = generateSecret()
val recoveryKey = generateRecoveryKey()  // User stores offline
val encryptedSecret = encryptRecoverySecret(userSecret, recoveryKey)

// Store encrypted secret separately (non-transient knowledge)
class UserRecovery {
    @Id var userId: CID? = null
    var encryptedSecret: String? = null  // Not in Encryptable entity
}

// Recovery: user provides recovery key → decrypt secret → access data
```

**3. Hybrid approach (non-transient knowledge):**
```kotlin
// Use @Id instead of @HKDFId
class User : Encryptable<User>() {
    @Id override var id: CID? = null  // Not derived from secret
    
    @Encrypt var email: String? = null
    @Encrypt var sensitiveData: String? = null
}

// Store username → userId mapping (allows password reset)
// But: Loses transient knowledge benefits
```

**4. Account re-creation:**
```kotlin
// Lost password = create new account
// Old data remains encrypted, inaccessible
// User starts fresh
// This is the pure transient knowledge approach
```

**Recommendation:**
- **High security apps** (password managers, health): Use backup codes, accept data loss on secret loss
- **Consumer apps** (e-commerce, social): Use hybrid approach with traditional password reset
- **Enterprise apps**: Integrate with SSO (Okta, Auth0) - delegate auth, use Encryptable for data encryption

**Key insight:** Transient knowledge is incompatible with "forgot password." Choose based on your security requirements.

---

### 🔍 Can admins see any user data?

**It depends on your architecture choices:**

**Scenario 1: Full transient knowledge (@HKDFId + full @Encrypt):**
```kotlin
class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
    @Encrypt var name: String? = null
}
```
**Admin capabilities:**
- ❌ Cannot see email, name (encrypted)
- ❌ Cannot decrypt data (no secret)
- ❌ Cannot search by email (encrypted)

**Verdict:** Admins have ZERO access to user data. True zero-knowledge.

---

**Scenario 2: Hybrid (searchable metadata + encrypted PII):**
```kotlin
class User : Encryptable<User>() {
    @HKDFId override var id: CID? = null
    
    // Searchable by admin
    var username: String? = null
    var accountStatus: String? = null
    var createdAt: Instant? = null
    
    // Encrypted (admin cannot see)
    @Encrypt var email: String? = null
    @Encrypt var address: Address? = null
}
```
**Admin capabilities:**
- ✅ Can search by username
- ✅ Can see account status, creation date
- ❌ Cannot see email, address (encrypted)
- ✅ Can ban/suspend users (via username)

**Verdict:** Admins can manage accounts but cannot see PII.

---

**Scenario 3: Traditional approach (@Id + partial encryption):**
```kotlin
class User : Encryptable<User>() {
    @Id override var id: CID? = null
    
    var username: String? = null
    var email: String? = null  // NOT encrypted (admin can see)
    @Encrypt var ssn: String? = null  // Encrypted (admin cannot see)
}
```
**Admin capabilities:**
- ✅ Can enumerate all users (`findAll()`)
- ✅ Can see email (not encrypted)
- ✅ Can search by email
- ❌ Cannot see SSN (encrypted)

**Verdict:** Admins can do everything except access highly sensitive fields.

---

**Choose based on requirements:**
- **Healthcare, finance:** Scenario 1 (zero admin access)
- **General SaaS:** Scenario 2 (admin can manage, not see PII)
- **Internal tools:** Scenario 3 (admin has broad access)

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

