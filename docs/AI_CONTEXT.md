# Encryptable — AI Context Document

> **Purpose:** This document gives a Claude instance (or any AI assistant) complete working
> knowledge of the Encryptable project: what it is, how it works, its design philosophy,
> its real innovations, its honest limitations, its key source files, and the nuanced
> judgements that took hours of analysis to reach. Read this before touching any file.

---

## 1. What Encryptable Is

Encryptable is a **Kotlin-first, Spring Boot 4 + MongoDB ODM** (Object-Document Mapper) built
around a single coherent idea:

> **The correct thing should be the easy thing. The incorrect thing should be impossible or
> at least loud.**

It is **not** just a security framework bolted onto a persistence layer. It is a persistence
layer where the data model itself enforces cryptographic correctness, referential integrity,
and privacy invariants — by default, automatically, without developer discipline.

**Current version:** 1.0.9 (as of 2026-03-05)
**License:** MIT
**Group:** `tech.wanion`
**Artifact:** `encryptable`

**Tech stack:**
- Kotlin 2.2.21 + Spring Boot 4.0.3
- Spring Data MongoDB + AspectJ 1.9.x (post-compile weaving)
- Java 21+ (virtual threads required)
- HKDF via `at.favre.lib:hkdf:2.0.0` (RFC 5869)
- AES-256-GCM for all field encryption

---

## 2. Core Abstractions — Know These Cold

### 2.1 `Encryptable<T>`

The base class every entity extends. Holds:
- `private var secret: String?` — never persisted, never public, wiped at request end
- `encryptableFieldMap: MutableMap<String, String>` — field name → secret of nested entity
- `encryptableFieldTypeMap: MutableMap<String, String>` — field name → concrete class name (polymorphism)
- `encryptableListFieldMap: MutableMap<String, MutableList<String>>` — field name → list of nested secrets
- `storageFields` / `storageFieldIdMap` — tracks which ByteArray fields are stored externally

Key methods:
- `withSecret(secret)` — sets secret, returns `this`
- `hashCodes()` — computes per-field hashCodes for dirty checking
- `touch()` — hook for audit fields (override in subclass)
- `prepare()` (private) — called on first save; sets ID, encrypts fields
- `restore(secret)` (private) — called on load; decrypts fields
- `rotateSecret(oldSecret, newSecret)` — re-encrypts everything under new secret

### 2.2 CID (Compact ID)

A 22-character URL-safe Base64 encoding of 16 bytes (128 bits). Always exactly 22 chars.
Stored as `Binary` in MongoDB. Two strategies:

| Strategy | Annotation | How CID is derived | Encryption key used |
|---|---|---|---|
| Direct | `@Id` | secret IS the CID (22-char Base64 input) | **master secret** |
| HKDF | `@HKDFId` | `HKDF(secret, className, "CID", 16 bytes)` | **entity's own secret** |

`@HKDFId` is recommended for user data. `@Id` is for public/shared resources.
`@HKDFId` provides per-entity cryptographic isolation — the master secret is never involved.

### 2.3 Annotations

| Annotation | Meaning |
|---|---|
| `@HKDFId` | ID derived from secret via HKDF. Per-entity key isolation. |
| `@Id` | Secret IS the CID. Master secret encrypts `@Encrypt` fields. |
| `@Encrypt` | AES-256-GCM field encryption. Works on `String`, `ByteArray`, `List<String>`. |
| `@PartOf` | Marks a field/list as owned by this entity → cascade delete on parent delete/rotation. |
| `@Sliced(sizeMB)` | Splits a `ByteArray` field into independent `sizeMB`-sized chunks stored separately. Each slice encrypted independently. Transparent to the developer. `sizeMB` 1–32, default 4. |
| `@EnableEncryptable` | Spring Boot autoconfiguration trigger. Goes on the main `@SpringBootApplication` class. |

### 2.4 HKDF Context Separation

All HKDF derivations use a mandatory context string to ensure independence (RFC 5869 §3.2):

```
HKDF(secret, "$className:CID")            → entity's MongoDB _id
HKDF(secret, "$className:ENCRYPTION_KEY") → AES-256 encryption key
```

Same secret + different context = cryptographically independent outputs.
This is why the CID can be public (stored in DB) without revealing the encryption key.

### 2.5 The Repository

Every entity needs a repository:

```kotlin
@Repository
interface UserRepository : EncryptableMongoRepository<UserEntity>
```

Key methods (all secret-based, never ID-based for sensitive data):
- `findBySecretOrNull(secret)` → loads + decrypts, registers for dirty checking
- `findAllBySecrets(secrets)` → bulk load + decrypt
- `save(entity)` → insert only (not upsert). Auto-detects new entities.
- `deleteBySecret(secret)` → cascade deletes all `@PartOf` children + storage files
- `rotateSecret(oldSecret, newSecret)` → re-encrypts everything atomically
- `existsBySecret(secret)` → existence check without loading
- `filterExistingSecrets(secrets)` / `filterNonExistingSecrets(secrets)` → bulk existence

**Automatic updates:** Entities loaded via `findBySecretOrNull` are tracked by hashCode. At
request end (`afterCompletion`), changed fields are automatically sent as `$set` partial
updates. **Developer never calls `update()` manually.**

### 2.6 The Six Aspects (AspectJ)

| Aspect | What it does |
|---|---|
| `EncryptableByteFieldAspect` | Intercepts `get`/`set` on `ByteArray` fields → delegates to `StorageHandler` |
| `EncryptableFieldAspect` | Intercepts `get`/`set` on `Encryptable` fields → lazy loads nested entity on get, saves/deletes on set |
| `EncryptableListFieldAspect` | Intercepts `get` on `List<Encryptable>` fields → initializes `EncryptableList` |
| `EncryptableIDAspect` | Intercepts `@HKDFId`/`@Id` field access to enforce ID generation strategy |
| `EncryptableMetadataAspect` | Caches `Metadata` per entity class |
| `EncryptablePrepareAspect` | Intercepts `prepare()` → calls `StorageHandler.prepare()` for ByteArray routing |

**Critical:** AspectJ post-compile weaving is **mandatory**. The framework does not work
without it. Uses `io.freefair.aspectj.post-compile-weaving` Gradle plugin.

### 2.7 StorageHandler + IStorage

Binary data above 16KB (configurable via `encryptable.storage.threshold`) is routed to an
external storage backend automatically — no annotation required for GridFS (default).
Custom backends register via a meta-annotation linked to an `IStorage<R>` implementation.

`IStorage<R>` interface — only 3 operations: `create`, `read`, `delete`.

The `StorageHandler.set()` method implements **atomic replace**:
1. Creates new file in storage FIRST
2. Sets field in memory
3. Deletes old file ONLY after successful create

A `ByteArray` field IS its storage state. Assign → stored. Null → deleted. Read → lazily
fetched and decrypted. Developer never touches the storage API.

**`@Sliced` fields** use a fundamentally different storage layout:
- The entity document holds a concatenated reference `ByteArray` with a 4-byte big-endian
  length header followed by N × `referenceLength` slice references.
- Each slice is independently encrypted (own IV + auth tag, AES-256-GCM).
- On read, all slices are fetched in parallel and reassembled in exact order using the
  pre-allocated output array from the 4-byte length header — no intermediate buffer.
- On update, new slices are created before old ones are deleted (atomic replace per slice).
- On delete, all N references are removed.
- `@Sliced` does **not** require changing or implementing `IStorage` — any existing backend works.
- Slicing is entirely transparent to the developer; field assignment/read behavior is identical.

### 2.8 EncryptableList

Thread-safe `MutableList<T : Encryptable<T>>` proxy that:
- Lazy loads elements on first access (batch)
- Persists mutations atomically (DB success required before in-memory change)
- Cascade deletes on `removeAt`/`clear` if field is `@PartOf`
- Automatically initializes from plain `MutableList` during `prepare()`

### 2.9 EncryptableInterceptor

`HandlerInterceptor.afterCompletion` runs at the end of every HTTP request:
1. Flushes all dirty-tracked entities (partial `$set` updates)
2. Cascade-deletes any unsaved new entities (orphan prevention)
3. Clears all thread-local state
4. Calls `wipeMarked()` — zerifies all registered secrets, keys, and decrypted data from memory

If wiping fails → framework throws. Fail-fast on memory hygiene. Never silent.

### 2.10 MasterSecretHolder

Holds the application-wide master secret used for encrypting `@Id` entity fields.
Configured via:
- `ENCRYPTABLE_MASTER_SECRET` environment variable
- `encryptable.master.secret` application property
- `MasterSecretHolder.setMasterSecret()` programmatically

Not required if only `@HKDFId` entities are used.

---

## 3. Design Philosophy — The Most Important Section

### "The correct thing should be the easy thing"

Every design decision in the framework can be evaluated against this principle:

- **Automatic dirty checking** → developer cannot accidentally not save, or over-save
- **Automatic orphan cleanup** → developer cannot accidentally leave GridFS files or child entities behind
- **`EncryptableList` atomicity** → DB and in-memory state cannot diverge by accident
- **Memory wipe at `afterCompletion`** → developer cannot accidentally leave secrets in heap
- **`NotImplementedError` on unsafe repository methods** → developer cannot accidentally bypass the secret-based access model
- **Fail-fast on wipe failure** → security property is a hard guarantee, not a best-effort

### "The composition is the innovation"

None of the individual building blocks are novel (HKDF, AES-GCM, AspectJ, dirty checking,
cascade delete). The innovation is that they are composed into a unified model where:

1. The secret derives the address (HKDF → CID = MongoDB `_id`)
2. The secret derives the encryption key (HKDF → AES key, different context)
3. Relationships store secrets of child entities in the parent's `encryptableFieldMap`
4. Lazy loading uses those stored secrets to fetch and decrypt children
5. Rotation re-encrypts everything including those stored secrets
6. `@PartOf` + rotation orphans children that should no longer exist

Because all five of these compose correctly, **entirely new patterns emerge for free** —
most dramatically, the recovery code pattern.

---

## 4. The Recovery Code Pattern — The Framework's Sharpest Proof

This is the clearest demonstration that the abstraction is correctly leveled.

### The insight

`RecoveryCodeEntity` holds a `var userEntity: UserEntity?` field (no `@PartOf`).
`UserEntity` holds a `@PartOf var recoveryCodes: MutableList<RecoveryCodeEntity>`.

When `RecoveryCodeEntity` is saved, the framework stores `userSecret` in
`encryptableFieldMap["userEntity"]` — encrypted with the recovery code's own HKDF-derived
key. This is just the standard nested-entity relationship mechanism.

**Recovery flow:**
```kotlin
// That's it. The entire recovery flow.
val codeEntity = recoveryCodeRepository.findBySecretOrNull(recoveryCode)
    ?: throw IllegalArgumentException("Invalid or already-used recovery code")
val user = codeEntity.userEntity       // AspectJ lazy-loads UserEntity transparently
val userSecret = Encryptable.getSecretOf(user)!!
userRepository.rotateSecret(userSecret, newSecret)
generateRecoveryCodes(newSecret, userRepository)
markForWiping(userSecret, recoveryCode)
```

### Why this is unprecedented

- **Zero cryptographic code** in the recovery flow. No `AES256.decrypt`, no HKDF call, no IV.
- **No user identifier required** — `findBySecretOrNull(recoveryCode)` gives you the user directly.
- **No email lookup, no CID storage by the user** — the recovery code IS the addressing mechanism.
- **One-time use enforced by rotation** — after `rotateSecret`, old code entities are orphaned.
  `findBySecretOrNull(oldCode)` returns null automatically.
- **Server never has agency in recovery** — it cannot initiate, assist, or observe recovery.
  A recovery request is structurally identical to a normal login from the server's perspective.

Traditional recovery: server stores a reset token / escrow key / recovery email. Server has agency.
Encryptable recovery: server is a passive lookup table. User's offline code is the only key.

This pattern was not designed into the framework. It **emerged** from the composition of
`@HKDFId`, nested entity references, `@PartOf`, and `rotateSecret` — which is the strongest
possible evidence that the primitives are correctly chosen.

---

## 5. Genuine Innovations (Ranked by Strength)

### 5.1 Field-as-Live-Mirror (9/10 — strongest implementation)
`ByteArray` assignment → encrypted → stored externally → lazily fetched → decrypted → returned.
All via AspectJ field-level interception (`get(byte[] ...)` / `set(byte[] ...)`).
Atomic replace (create-before-delete). Zero orphan guarantee. Pluggable backends.
Developer never calls a storage API.

**No other framework combines all of this behind a plain field assignment.**

### 5.2 Cryptographic Addressing (8/10 — strongest idea)
`HKDF(secret, className, "CID", 16 bytes)` → MongoDB `_id`.
Eliminates the username→user_id mapping table entirely.
O(1) user lookup without storing any user identity.
Same secret + different entity class = different CID (automatic namespacing).

**Novel application of content-addressable principles to relational data modeling.**

### 5.3 Transparent Polymorphism (8/10)
Abstract field types automatically preserve concrete type across save/load.
`encryptableFieldTypeMap` stores concrete class name only when it differs from declared type.
AspectJ resolves correct type and loads from correct repository on lazy load.
Zero annotations. Zero configuration.

**Spring Data MongoDB needs `@TypeAlias`. Hibernate needs `@Inheritance`. Encryptable needs nothing.**

### 5.4 Recovery Code Pattern (emergent — not a designed feature)
As described in §4. The most important thing to understand about the framework.

### 5.5 `@Encrypt` — Single Annotation, Maximum Work
AES-256-GCM + HKDF key derivation + unique IV + per-user isolation + parallel processing +
automatic GridFS routing for large `ByteArray` + key wiping. One annotation.

### 5.6 Parallel Hash-Based Change Detection
Per-field hashCode comparison (parallelized). First-4KB checksum for `ByteArray`.
Works on encrypted fields (hash computed before encryption).
Enables automatic partial `$set` updates.

---

## 6. Honest Limitations — Know These Before Advising

| Limitation | Nature | Workaround |
|---|---|---|
| **MongoDB + Spring only** | Architectural | None — not designed to be generic |
| **No Kotlin coroutines** | Hard incompatibility (ThreadLocal) | Use `Limited` utilities for parallelism |
| **No GraalVM native image** | AspectJ limitation | None currently |
| **No professional security audit** | Resourcing | Suitable for general apps; requires audit for HIPAA/PCI |
| **Class/package immutability** | HKDF uses class name in derivation | Cannot rename entity classes after data exists |
| **Encrypted fields not queryable** | By design (proves encryption is real) | Query unencrypted fields; filter in-memory |
| **CID-only identifiers** | By design | No UUID, Long, int support |
| **No soft delete** | By design (GDPR compliance) | Hard delete only |
| **Secret loss without recovery codes = data loss** | By design (transient knowledge) | Generate recovery codes at registration |
| **AspectJ setup friction** | Tooling | `io.freefair.aspectj.post-compile-weaving` Gradle plugin required |
| **`@PartOf` + secret rotation breaks parent references** | Architecture | Do not rotate secrets of `@PartOf` child entities |
| **Java 21+ required** | Virtual threads | No workaround |

---

## 7. Security Model — Be Precise About This

**What Encryptable is:** Transient-knowledge (request-scoped) encryption.
**What it is NOT:** Zero-knowledge.

The difference matters:
- **Zero-knowledge:** Secrets never reach the server. All crypto is client-side.
- **Transient knowledge:** Secrets are on the server, in memory, for the duration of one request. Then wiped.

The server **can** decrypt user data during a request (that's how it serves the API).
A compromised JVM process, a malicious operator, or a memory dump during an active request
can get plaintext. The framework minimises the window (single request) and the footprint
(`markForWiping` + `afterCompletion` wipe), but cannot eliminate it.

**What the model does protect against:**
- Database dump / stolen disk → ciphertext only, useless without secrets
- Database admin reading data → cannot, no secrets stored
- Insider threat (employee with DB access) → cannot read user data
- Admin password reset → structurally impossible
- User enumeration → CIDs are HKDF outputs, look random
- Credential stuffing (no stored passwords/hashes) → nothing to stuff against
- Server-side recovery (admin helping a "locked out" user) → impossible by design

**The v1.0.3 terminology fix:** The framework initially claimed zero-knowledge. This was
corrected in v1.0.3 with a public apology. Current docs prominently say "NOT zero-knowledge."
This intellectual honesty is a deliberate project value.

---

## 8. Version History — Key Events

| Version | Date | Key change |
|---|---|---|
| 1.0.0 | 2025-12-12 | Initial release |
| 1.0.3 | 2025-12-20 | Terminology: "zero-knowledge" → "transient knowledge" + apology |
| 1.0.4 | 2026-01-07 | Master secret support for `@Id` entities |
| 1.0.5 | 2026-01-18 | Removed `createdByIP`/`createdAt` from base class (cross-reference leak vectors) |
| 1.0.6 | 2026-01-24 | Bulk update support + performance |
| 1.0.7 | 2026-01-30 | Polymorphic relationship support |
| **1.0.8** | **2026-02-27** | **Critical security fix: `@Id` + `@Encrypt` + `ByteArray` was using CID as key instead of master secret (1.0.4–1.0.7 bug). Storage abstraction refactor. Mandatory migration on first run.** |
| 1.0.9 | current | Current stable |

### The 1.0.8 Bug — Important to Understand

In 1.0.4–1.0.7, `ByteArray` fields annotated `@Encrypt` on `@Id` entities were encrypted
with the entity's CID (a **public** value stored in the database as `_id`) instead of the
master secret. This made the encryption of those fields effectively worthless.

It was a **missed callsite** during the 1.0.4 refactoring — not a misunderstanding of
cryptography. Self-discovered during the 1.0.8 storage refactoring. Fixed, disclosed
publicly in `MISSED_CALLSITE_BUG_1_0_8.md`, and a migration was provided.

This is why `EncryptableKeyCorrectnessTest` exists — it bypasses the framework's own decrypt
path to verify that the correct key was actually used to encrypt each field type and strategy
combination. Standard round-trip tests would have passed even with the bug.

---

## 9. Test Suite — 105 Tests Across 16 Files

| Test class | Tests | What it covers |
|---|---|---|
| `ApplicationTests` | 1 | Context loads |
| `CryptoPropertiesTest` | 6 | AES-GCM round-trip, HKDF determinism, IV uniqueness (no DB) |
| `EncryptableBasicTest` | 8 | CRUD, encryption, change detection, batch ops |
| `EncryptableChangeDetectionTest` | 10 | Dirty checking, partial updates, `touch()` |
| `EncryptableCustomStorageTest` | 1 | Custom `IStorage` backend via `@MemoryStorage` annotation |
| `EncryptableIDStrategyTest` | 7 | `@HKDFId` vs `@Id`, determinism, consistency |
| `EncryptableIntegrationTest` | 11 | Complex multi-field scenarios, concurrency, edge cases |
| `EncryptableKeyCorrectnessTest` | **14** | **Bypasses framework decrypt path. Verifies correct key per field type + strategy. The most important test.** |
| `EncryptableListTest` | 8 | `EncryptableList`, add/remove, cascade, lazy load |
| `EncryptableNestedEntitiesTest` | 5 | Relationships, cascade delete, `@PartOf` |
| `EncryptablePolymorphicTest` | 7 | Polymorphic fields, type preservation, lazy load |
| `EncryptableSecretRotationTest` | 1 | `rotateSecret`, data preservation |
| `EncryptableSlicedStorageTest` | 9 | `@Sliced` full lifecycle: save, round-trip, multi-slice boundary, key correctness (bypasses decrypt path), update, delete |
| `EncryptableStorageRotationTest` | 1 | `rotateSecret` with storage-backed fields |
| `EncryptableStorageTest` | 9 | Large binary fields, lazy loading, storage lifecycle |
| `EncryptableUnsavedCleanupTest` | 7 | Orphan prevention for entities never saved |

---

## 10. Source File Map

```
src/main/kotlin/tech/wanion/encryptable/
├── EncryptableContext.kt          — Spring context holder, repository registry, IP extraction, wipeMarked()
├── MasterSecretHolder.kt          — Master secret storage (env var / property / programmatic)
├── aop/
│   ├── EncryptableByteFieldAspect.kt    — ByteArray field get/set → StorageHandler
│   ├── EncryptableFieldAspect.kt        — Encryptable field get/set → lazy load + save/delete
│   ├── EncryptableIDAspect.kt           — ID field access enforcement
│   ├── EncryptableListFieldAspect.kt    — List<Encryptable> → EncryptableList init
│   ├── EncryptableMetadataAspect.kt     — Metadata caching
│   └── EncryptablePrepareAspect.kt      — prepare() → StorageHandler.prepare()
├── config/
│   └── EncryptableConfig.kt             — storageThreshold, integrityCheck, etc.
├── mongo/
│   ├── CID.kt                           — Compact ID (22-char Base64url of 16 bytes)
│   ├── Encrypt.kt                       — @Encrypt annotation
│   ├── Encryptable.kt                   — Base class (~1,116 lines). The heart of everything.
│   ├── EncryptableInterceptor.kt        — afterCompletion: flush dirty, cleanup orphans, wipe secrets
│   ├── EncryptableList.kt               — Thread-safe lazy-loading atomic list proxy
│   ├── EncryptableMongoRepository.kt    — Interface: all secret-based operations
│   ├── EncryptableMongoRepositoryImpl.kt — Implementation (~1,356 lines)
│   ├── HKDFId.kt                        — @HKDFId annotation
│   ├── PartOf.kt                        — @PartOf annotation
│   └── converter/                       — MongoDB converters
│       ├── cid/
│       │   ├── CIDFromBinary.kt         — @ReadingConverter: Binary → CID
│       │   └── CIDToBinary.kt           — @WritingConverter: CID → Binary
│       ├── map/
│       │   ├── DocumentToMapConverter.kt — @ReadingConverter: Document → ConcurrentHashMap
│       │   └── MapToDocumentConverter.kt — @WritingConverter: Map → Document
│       ├── ListToNullConverter.kt       — @WritingConverter: empty List → null (commented out by default)
│       └── NullToEmptyListConverter.kt  — @ReadingConverter: ArrayList → Collections.synchronizedList (symmetric counterpart to ListToNullConverter)
├── storage/
│   ├── GridFSStorage.kt                 — Default storage backend (ObjectId reference, 12 bytes)
│   ├── IStorage.kt                      — Interface: create / read / delete (29 lines)
│   ├── Sliced.kt                        — @Sliced annotation: chunked storage with length-prefixed reference array. sizeMB 1–32, default 4.
│   ├── SlicedResult.kt                  — Parsed slice metadata: originalLength + ordered list of per-slice references
│   ├── Storage.kt                       — Meta-annotation for custom backends
│   └── StorageHandler.kt                — Routes ByteArray to storage; atomic replace; threshold check; sliced read/write
└── util/
    ├── AES256.kt                        — AES-256-GCM encrypt/decrypt + HKDF key derivation
    ├── HKDF.kt                          — RFC 5869 expand-only (high-entropy input assumed)
    ├── Limited.kt                       — Bounded parallelForEach (preserves ThreadLocal)
    ├── SecurityUtils.kt                 — Shannon entropy + uniqueness checks for CID validation
    ├── SHA256.kt / SHA512.kt            — Utility hashes
    └── extensions/                      — Kotlin extensions (markForWiping, readField, first4KBChecksum, etc.)
```

---

## 11. Common Patterns — Use These as Templates

### Basic entity + repository

```kotlin
@Document(collection = "users")
class UserEntity : Encryptable<UserEntity>() {
    @HKDFId override var id: CID? = null
    @Encrypt var email: String? = null
    @Encrypt var name: String? = null
    @PartOf var address: AddressEntity? = null           // One-to-one, cascade delete
    @PartOf var recoveryCodes: MutableList<RecoveryCodeEntity> = mutableListOf() // One-to-many
}

@Repository
interface UserRepository : EncryptableMongoRepository<UserEntity>
```

### Usage

```kotlin
// Create
val user = UserEntity().withSecret(secret).apply { email = "a@b.com" }
userRepository.save(user)

// Read (auto-decrypts, registers for dirty tracking)
val user = userRepository.findBySecretOrNull(secret) ?: return

// Update (just change the field — afterCompletion sends $set automatically)
user.email = "new@b.com"
// No save() call needed

// Delete (cascade-deletes address + recoveryCodes)
userRepository.deleteBySecret(secret)

// Rotate secret (re-encrypts everything)
userRepository.rotateSecret(oldSecret, newSecret)
```

### Recovery codes (the pattern — see §4 for full explanation)

```kotlin
@Document(collection = "recovery_codes")
class RecoveryCodeEntity : Encryptable<RecoveryCodeEntity>() {
    @HKDFId override var id: CID? = null
    var userEntity: UserEntity? = null   // no @PartOf — user owns codes, not the reverse
}

// Generation (at registration)
val codes = (1..5).map { CID.randomCIDString() + CID.randomCIDString() }
val entities = codes.map { code -> RecoveryCodeEntity().withSecret(code).apply { userEntity = user } }
user.recoveryCodes.addAll(entities)
userRepository.save(user)
// return codes to user — show once, never store

// Recovery
val codeEntity = recoveryCodeRepository.findBySecretOrNull(recoveryCode)
    ?: throw IllegalArgumentException("Invalid or already-used recovery code")
val user = codeEntity.userEntity!!
val userSecret = Encryptable.getSecretOf(user)!!
userRepository.rotateSecret(userSecret, newSecret)
// regenerate codes under newSecret
```

### Custom storage backend

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Storage(storageClass = S3StorageImpl::class)
annotation class S3Storage

class S3StorageImpl : IStorage<String> {
    override val referenceLength = 16
    override fun createReference(bytes: ByteArray?) = bytes?.let { String(it) }
    override fun bytesFromReference(ref: String) = ref.toByteArray()
    override fun create(meta: String, bytes: ByteArray): String { /* upload to S3 */ }
    override fun read(meta: String, ref: String): ByteArray? { /* download from S3 */ }
    override fun delete(meta: String, ref: String) { /* delete from S3 */ }
}

// Usage — field behavior identical to GridFS
class Document : Encryptable<Document>() {
    @HKDFId override var id: CID? = null
    @Encrypt @S3Storage var file: ByteArray? = null
}
```

---

## 12. What "Good" Looks Like in This Codebase

When writing code that uses Encryptable, a solution is right when:

1. **It uses existing primitives** — if you're writing encrypt/decrypt calls in application code, something is wrong
2. **It has no cryptographic code** — key derivation, IV generation, and encryption belong inside the framework
3. **It composes from `@HKDFId` + relationship + `@PartOf` + `rotateSecret`** — these four cover almost everything
4. **It trusts the framework** — lazy loading, dirty tracking, orphan cleanup, and memory wiping happen automatically
5. **It has no manual cleanup** — if you're deleting child entities explicitly, you probably should be using `@PartOf`

The recovery code pattern (§4) is the gold standard: the security guarantee is **inherited** from
the framework's primitives, not **built** on top of them.

---

## 13. Rating and Context

Evaluated honestly across two dimensions:

**Innovation: 7.5–8.5/10**
- Genuinely novel: cryptographic addressing, field-as-live-mirror, transparent polymorphism
- Emergent novelty: recovery code pattern (not designed in; fell out of correct composition)
- Standard engineering: AES-GCM, HKDF, dirty checking, relationship management
- Overcounted in docs: "9 innovations" is really 5-6 (some are consequences of others)

**As a framework for its stated goal (privacy-first, straightforward, less error-prone): 8.5/10**
- Ceiling: MongoDB-only, no coroutines, no GraalVM, no independent security audit
- What earns the score: the composition model, the automatic correctness guarantees, the
  recovery code pattern as a proof of abstraction quality

**The author (WanionCane)** has demonstrated strong cryptographic awareness, willingness to
correct mistakes publicly (the 1.0.3 terminology fix, the 1.0.8 bug disclosure), and
architectural thinking that goes beyond feature accumulation. The framework is a solo/small-team
project at v1.0.9 and already has more architectural coherence than most mature ORMs.

---

## 14. Documents Worth Reading (in Priority Order)

1. `docs/INNOVATIONS.md` — feature overview and comparisons
2. `docs/RECOVERY_CODES.md` — the recovery code pattern (strongest proof of abstraction quality)
3. `docs/MISSED_CALLSITE_BUG_1_0_8.md` — security transparency and the key correctness problem
4. `docs/LIMITATIONS.md` — honest constraints (read before advising on adoption)
5. `docs/NOT_ZERO_KNOWLEDGE.md` — precise security model
6. `docs/STORAGE.md` — field-as-live-mirror deep dive
7. `docs/concepts/TRANSIENT_KNOWLEDGE_AUTH.md` — full 2FA + recovery auth model
8. `src/test/kotlin/cards/project/mongo/EncryptableKeyCorrectnessTest.kt` — most important test

---

*Generated: 2026-03-07 | Framework version: 1.0.9 | Author: Claude Sonnet 4.6*



