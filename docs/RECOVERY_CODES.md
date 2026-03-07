# Recovery Codes in Encryptable

## 🎯 Overview

Recovery codes allow users to regain access to their data after losing their primary secret —
without the server ever storing or learning the secret, and **without the server needing to
know anything about the user's identity**.

Each recovery code is a dedicated `RecoveryCodeEntity` whose secret **is** the recovery code.
It holds a direct reference to the `UserEntity`. When the user presents a recovery code,
the server calls `findBySecretOrNull(recoveryCode)` — and Encryptable transparently loads
the linked `UserEntity`, its secret, and everything it owns. No user identifier, no email
lookup, no CID storage required.

Using the recovered secret is, by design, an instance of **secret rotation** — the existing
`rotateSecret` mechanism handles everything from there.

---

## 🔐 How It Works

### The insight

`RecoveryCodeEntity` has a `userEntity: UserEntity` field. When the framework saves it, it
stores the `UserEntity`'s secret inside `RecoveryCodeEntity`'s `encryptableFieldMap` —
encrypted, as it does for every nested `Encryptable` relationship. When you load
`RecoveryCodeEntity` by its secret (the recovery code), the framework lazily loads
`UserEntity` from that map automatically.

**The recovery code secret → decrypts → RecoveryCodeEntity → lazy-loads → UserEntity → its secret → rotateSecret.**

The server never manually handles a user secret. Encryptable handles everything.

```
findBySecretOrNull(recoveryCode)
    → RecoveryCodeEntity loaded
    → recoveryCodeEntity.userEntity  (lazy load triggered by AspectJ)
        → framework reads encryptableFieldMap["userEntity"] = userSecret
        → UserEntity loaded and decrypted with userSecret
    → userSecret now available via Encryptable.getSecretOf(recoveryCodeEntity.userEntity)
    → rotateSecret(userSecret, newSecret)
```

---

## 🏗️ Implementation

### Entities

```kotlin
@Document(collection = "recovery_codes")
class RecoveryCodeEntity : Encryptable<RecoveryCodeEntity>() {

    @HKDFId
    override var id: CID? = null

    // Direct reference to the user. No @PartOf — the user owns the codes, not the other way around.
    // The framework stores the userEntity's secret in encryptableFieldMap and lazy-loads it on access.
    var userEntity: UserEntity? = null
}
```

```kotlin
@Document(collection = "users")
class UserEntity : Encryptable<UserEntity>() {

    @HKDFId
    override var id: CID? = null

    @Encrypt var email: String? = null

    // @PartOf: cascade-delete all recovery codes when the user is deleted,
    // and orphan/replace them automatically on rotateSecret.
    // @SimpleReference: the UserEntity does not own the recovery codes' secrets —
    // each secret IS the recovery code, known only to the user. The framework stores
    // only the code entity's ID, so the user can never re-read the plaintext codes
    // through this reference after creation. Deletion still works via @PartOf.
    @PartOf
    @SimpleReference
    var recoveryCodes: MutableList<RecoveryCodeEntity> = mutableListOf()
}
```

```kotlin
@Repository
interface RecoveryCodeRepository : EncryptableMongoRepository<RecoveryCodeEntity>

@Repository
interface UserRepository : EncryptableMongoRepository<UserEntity>
```

---

### Generating recovery codes at registration

Called once, immediately after the first `userRepository.save(user)`:

```kotlin
fun generateRecoveryCodes(
    userSecret: String,
    userRepository: UserRepository
): List<String> {
    val user = userRepository.findBySecretOrNull(userSecret)
        ?: throw IllegalStateException("User not found")

    // Generate 5 cryptographically secure recovery codes
    val codes = (1..5).map { CID.randomCIDString() + CID.randomCIDString() } // 44-char token

    // Each RecoveryCodeEntity's secret IS the recovery code.
    // Assigning user to userEntity causes the framework to store userSecret
    // in the entity's encryptableFieldMap — encrypted, automatically.
    val codeEntities = codes.map { code ->
        RecoveryCodeEntity().withSecret(code).apply {
            userEntity = user
        }
    }

    user.recoveryCodes.addAll(codeEntities)
    userRepository.save(user)

    // Return plaintext codes to the caller — shown once, never stored
    return codes
}
```

> **Show once.** The plaintext codes are returned to the UI exactly once, at registration.
> They are never persisted anywhere on the server.

---

### Recovery flow

```kotlin
fun recoverWithCode(
    recoveryCode: String,
    newSecret: String,
    recoveryCodeRepository: RecoveryCodeRepository,
    userRepository: UserRepository
) {
    // 1. Load the RecoveryCodeEntity by the recovery code secret.
    //    If the code is wrong or already rotated away, this returns null.
    val codeEntity = recoveryCodeRepository.findBySecretOrNull(recoveryCode)
        ?: throw IllegalArgumentException("Invalid or already-used recovery code")

    // 2. Access userEntity — AspectJ intercepts the field get, reads encryptableFieldMap["userEntity"],
    //    and transparently loads + decrypts the UserEntity. No manual secret handling required.
    val user = codeEntity.userEntity
        ?: throw IllegalStateException("Recovery code entity has no linked user")

    // 3. Retrieve the user's current secret — it was set on the entity during lazy load.
    val userSecret = Encryptable.getSecretOf(user)
        ?: throw IllegalStateException("Could not retrieve user secret from loaded entity")

    // 4. Rotate to the new secret.
    //    Re-encrypts all user data, all @Encrypt fields, all storage-backed files,
    //    and all nested entities — including the recoveryCodes list.
    //    The @PartOf relationship orphans all old RecoveryCodeEntities.
    userRepository.rotateSecret(userSecret, newSecret)

    // 5. Generate a fresh set of recovery codes under the new secret.
    generateRecoveryCodes(newSecret, userRepository)

    markForWiping(userSecret, recoveryCode)
}
```

That is the entire recovery flow. The server never locates a user by email or CID.
The server never manually decrypts anything. Encryptable handles it all.

---

## 🔒 Security Properties

| Property | Guarantee |
|---|---|
| **Server never stores recovery codes** | Plaintext codes are shown once and never persisted |
| **No user identifier needed** | `findBySecretOrNull(recoveryCode)` gives everything — no email, no CID required |
| **DB dump yields nothing** | `encryptableFieldMap` is encrypted; without the recovery code, the user secret is inaccessible |
| **Per-entity isolation** | Each `RecoveryCodeEntity` has its own `@HKDFId` — different code = different encryption key |
| **One-time use enforced by rotation** | `rotateSecret` orphans old code entities; old recovery codes stop working immediately |
| **No replay** | Orphaned entities are unreachable — `findBySecretOrNull(oldCode)` returns null after rotation |
| **Transient knowledge preserved** | `userSecret` is in memory only for the duration of the rotation request |
| **Cascade delete** | `@PartOf` on `recoveryCodes` ensures all code entities are deleted when the user is deleted |
| **Auto-cleanup on rotation** | Old code entities are orphaned by rotation; new ones replace them in the same flow |
| **Codes unreadable after creation** | `@SimpleReference` on `recoveryCodes` means `UserEntity` stores only each code's ID — the plaintext recovery codes can never be re-read through the user reference after the initial generation |

---

## ⚠️ Important Constraints

### Recovery codes protect against *forgetting*, not *losing everything*

If a user never generated recovery codes, or has lost both their secret and all codes, recovery
is impossible. The right time to generate codes is immediately after successful registration.

### After rotation, generate new codes immediately

`rotateSecret` orphans the old `RecoveryCodeEntity` instances. Step 5 of the recovery flow
generates a fresh set under the new secret. A user who has just recovered has no valid codes
until new ones are generated — do not skip this step.

### Rate-limit the recovery endpoint

Each call to `findBySecretOrNull(recoveryCode)` is a primary-key lookup — O(1), fast, and
indistinguishable from a failed lookup from the server's perspective. Rate-limit at the
application or infrastructure level to prevent brute-force enumeration of recovery codes.

---

## 🔄 Why This Fits the Framework So Naturally

The entire recovery pattern uses nothing but features that already exist:

| Feature used | Why |
|---|---|
| `@HKDFId` on `RecoveryCodeEntity` | Secret = recovery code → per-entity isolation |
| Nested `Encryptable` field (`userEntity`) | Framework stores and lazy-loads the user automatically |
| `@PartOf @SimpleReference` list on `UserEntity` | Cascade delete + automatic orphaning on rotation; codes unreadable through the user reference after creation |
| `rotateSecret` | Re-encrypts everything — unchanged |
| `findBySecretOrNull` | Standard repository lookup — unchanged |

No new framework primitives. No new encryption logic. No manual secret handling.
The framework's existing composition does all the work.

```
Recovery:        findBySecretOrNull(recoveryCode) → .userEntity → getSecretOf(user) → rotateSecret(userSecret, newSecret)
Password change:                                                   getSecretOf(user) → rotateSecret(userSecret, newSecret)
```

See [Secret Rotation](SECRET_ROTATION.md) for full details of what `rotateSecret` does.

---

## 📋 Checklist for Implementors

- [ ] Create `RecoveryCodeEntity` with `var userEntity: UserEntity? = null` (no `@PartOf` on this field)
- [ ] Create `RecoveryCodeRepository`
- [ ] Add `@PartOf @SimpleReference var recoveryCodes: MutableList<RecoveryCodeEntity>` to `UserEntity`
- [ ] Call `generateRecoveryCodes()` immediately after the first `userRepository.save()`
- [ ] Show plaintext codes **once** — include a "I have saved my recovery codes" confirmation step
- [ ] Use two concatenated CIDs (44 chars) as minimum recovery code length
- [ ] After a successful recovery, immediately call `generateRecoveryCodes()` under the new secret
- [ ] Call `markForWiping` on `userSecret` and `recoveryCode` after rotation
- [ ] Rate-limit the recovery endpoint at the application or infrastructure level
- [ ] Allow users to regenerate codes (while logged in) to replace a lost set

---

## 🔗 Related Documents

- [Secret Rotation](SECRET_ROTATION.md) — the underlying mechanism recovery uses
- [ORM Features](ORM_FEATURES.md) — `@PartOf`, nested entities, and cascade delete
- [Transient Knowledge Authentication](concepts/TRANSIENT_KNOWLEDGE_AUTH.md) — full 2FA + recovery auth model
- [Limitations](LIMITATIONS.md) — recoverability is available via recovery codes
- [NO_PASSWORD_STORAGE.md](concepts/NO_PASSWORD_STORAGE.md) — authentication without storing credentials

---

*Last updated: 2026-03-05*

