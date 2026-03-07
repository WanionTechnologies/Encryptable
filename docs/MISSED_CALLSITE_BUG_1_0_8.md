# Security Transparency: The 1.0.8 Encryption Bug

> This document is part of Encryptable's commitment to full transparency on security matters.
> It covers what happened, how it was caught, how it was fixed, and what every developer
> should take away from it.

## TL;DR

In versions **1.0.4 through 1.0.7**, `ByteArray` fields annotated with `@Encrypt` on `@Id` entities were
being encrypted with the **entity's CID** — the MongoDB `_id` field, a **public value** — instead of the **master secret**.\
This meant the encryption key was stored in plaintext in the database, making the encryption of those fields effectively worthless.\
This was a **missed callsite bug** during a refactoring — not a misunderstanding of cryptography, not intentional.\
It was **self-discovered** during the 1.0.8 storage refactoring, fixed, and a migration was provided to re-encrypt affected data.

**This document exists to be transparent about what happened, explain the root cause clearly,
and remind every developer — including framework authors — that data safety demands relentless attention.**

---

## Background: What Changed in 1.0.4

Prior to version 1.0.4, only `@HKDFId` entities supported `@Encrypt` fields.\
All encryption in the framework used the **entity's own secret** as the key — which was the only secret that existed.

To understand why this was correct for `@HKDFId` but wrong for `@Id`, it helps to understand
how each strategy derives its CID and encryption key from the secret.

### @HKDFId: One Secret, Two Independent Derivations

For `@HKDFId` entities, the secret is used as input to **two separate HKDF derivations**, each
with a different context string:

```
secret + context "CID"            → HKDF → CID (MongoDB _id)
secret + context "ENCRYPTION_KEY" → HKDF → AES-256 encryption key
```

These two outputs are **cryptographically independent** — knowing the CID tells you nothing about
the encryption key, and vice versa. This is a fundamental property of HKDF: different context
strings produce outputs that are indistinguishable from independent random values.

Furthermore, the CID derivation is **one-way** — it is computationally infeasible to reverse the
CID back to the original secret. This is what makes `@HKDFId` safe: the CID can be stored
publicly in MongoDB as the `_id` field without exposing anything about the secret or the
encryption key.

So for `@HKDFId` entities: using the entity's own secret as the source for encryption key
derivation is **correct and secure**. The encryption key is never the secret itself — it is
always derived via HKDF with a dedicated context.

### @Id: The Secret IS the CID

For `@Id` entities, the design is intentionally simpler — and different:

```
secret (22-char Base64) = CID directly (no derivation)
```

There is no HKDF derivation for the CID — the secret *is* the CID, used directly as the MongoDB
`_id`. This is by design: `@Id` entities are meant for public identifiers where the ID is safe
to expose. The encryption key for these entities must therefore come from somewhere else entirely —
the **master secret**, which is never stored in the database.

This is why `getSecretFor()` exists: it abstracts the difference between the two strategies,
returning the entity's own secret for `@HKDFId` and the master secret for `@Id`.

In 1.0.4, **master secret support was introduced**, enabling `@Id` entities to also use `@Encrypt` fields.

Most of the encryption code was updated to call `getSecretFor()`. Most — but not all.

---

## The Bug: A Missed Callsite

Prior to 1.0.8, all `ByteArray` field logic — storage, encryption, and decryption — was
centralised in `EncryptableByteFieldAspect`. This aspect intercepted every get and set on
`ByteArray` fields in `Encryptable` subclasses and handled everything inline.

When master secret support was introduced in 1.0.4, the encryption paths in most of the
codebase were updated to call `getSecretFor()`. But the `EncryptableByteFieldAspect` was
**not updated** — it continued using the entity's own secret directly, as it had always done,
because that code path had never needed to behave differently before `@Id` + `@Encrypt` existed.

```kotlin
// What EncryptableByteFieldAspect was doing (wrong for @Id entities):
val secret = Encryptable.getSecretOf(encryptable)  // always entity secret — wrong for @Id

// What it should have been doing:
val secret = metadata.strategies.getSecretFor(encryptable)  // master secret for @Id, entity secret for @HKDFId
```

In 1.0.8, as part of the storage abstraction refactoring, **all `ByteArray` logic was moved out
of `EncryptableByteFieldAspect` and into `StorageHandler`**. `EncryptableByteFieldAspect` is now
a thin delegator — it intercepts the field access and immediately hands off to `StorageHandler`,
which contains all the actual logic. `StorageHandler` correctly calls
`metadata.strategies.getSecretFor(encryptable)` throughout.

This is precisely how the bug was found: while migrating the `ByteArray` logic into
`StorageHandler`, the entity secret being used directly in the old aspect became impossible to
miss. The fix was applied as part of the same refactoring.

The hard part of 1.0.8 was not the fix itself, but the **migration logic** to detect and
re-encrypt all previously affected fields with the correct key.

---

## Why This Was a Real Security Issue

As explained in the background above, for `@HKDFId` entities using the entity's own secret as
the encryption source is correct — the encryption key is always an independent HKDF derivation,
never the secret itself, and the CID is one-way and reveals nothing.

For `@Id` entities the situation is the opposite:

> **The entity's "secret" in the `@Id` strategy is the CID itself — the MongoDB `_id` field.**

The CID is a 22-character URL-safe Base64 string used as the document's primary key in the database.
It is, by design, **a public value** — stored in plaintext in MongoDB, visible in queries,
logs, and any system that has read access to the collection.

This means that in versions 1.0.4–1.0.7, `ByteArray @Encrypt` fields on `@Id` entities were
effectively encrypted with a key that anyone with database read access already had.

```
@Id entity CID:    "kL9mP3xR7wQ2vB5nH8jK1d"  ← stored in MongoDB _id, publicly visible
Encryption key:    "kL9mP3xR7wQ2vB5nH8jK1d"  ← same value, used as the key ← BUG
```

The encryption was not broken in a cryptographic sense — AES-256-GCM was still being used
correctly. But the key material had no confidentiality. Anyone who could read the `_id` field
could decrypt the `ByteArray` field. The encryption provided no protection whatsoever for
that specific field type on `@Id` entities.

The master secret, by contrast, is a high-entropy secret configured separately, never stored in
the database, and never derivable from anything in MongoDB. Using it as the key is what actually
provides the confidentiality guarantee that `@Encrypt` is supposed to deliver.

### The Silver Lining: The Public Key Made Migration Possible

There is a deeply ironic consequence of this bug: **the very thing that made it a security issue
is also what made it fully recoverable.**

Because the encryption key was the public CID — already sitting in the `_id` field of every
affected document — the migration could re-encrypt all affected data **without requiring any user
secrets**. The process was straightforward:

```
1. Read the document from MongoDB
2. Read the _id field (the CID — the wrong key that was used)
3. Decrypt the ByteArray field using the CID as the key
4. Re-encrypt the ByteArray field using the master secret (the correct key)
5. Write the document back
```

If the data had been encrypted with an unknown or lost key, recovery would have been impossible
and the data would have been permanently unreadable. The fact that the wrong key was always
recoverable from the database itself meant that **no data was lost**, and **no user intervention
was required** to perform the migration.

This does not reduce the severity of the bug — the data was genuinely unprotected during those
versions. But it is worth acknowledging that the specific nature of this bug allowed for a clean,
complete, and fully automated recovery.

---

## Who Was Affected

Only users who had **all three of the following** at the same time:
- An entity using `@Id` (not `@HKDFId`)
- A `ByteArray` field annotated with `@Encrypt`
- Running versions 1.0.4, 1.0.5, 1.0.6, or 1.0.7

If you were using `@HKDFId` entities (the recommended strategy for sensitive data), or if your `@Id`
entities had no `ByteArray` `@Encrypt` fields, **you were not affected.**

Versions 1.0.0–1.0.3 were also not affected, because `@Id` + `@Encrypt` did not exist yet.

---

## This Happens to Everyone

I want to be honest about something: **this class of bug happens to experienced developers at every level.**

### What a "missed callsite" bug looks like

When you introduce a new abstraction that changes behavior depending on context, you need to update
**every single caller**. Miss one, and that one path silently continues behaving the old way.

This is one of the most common bugs in software engineering:

- **OpenSSL's Heartbleed (2014):** A missing bounds check on a single code path.
  2 years in production. Affected millions of servers worldwide.
- **Log4Shell (2021):** A feature interaction in a single code path that had always existed but
  was only exploitable in a specific combination. 8+ years undetected.
- **Android's "Master Key" bug (2013):** A missed validation callsite in APK signature verification.
  Affected all Android versions at the time.

These are not examples of incompetent developers. They are examples of **how hard it is to maintain
complete awareness of every code path when a codebase evolves**.

The difference between a responsible project and an irresponsible one is not whether bugs happen —
**it's how they are handled when they do.**

---

## What This Should Teach You (As a Developer Using Any Framework)

> **Do not trust any framework blindly with data safety. Not Encryptable. Not Hibernate. Not anything.**

This is not pessimism — it is engineering discipline. Here is what you should take away:

### 1. Test the exact combination you rely on
The bug in 1.0.4–1.0.7 would have been caught immediately by a single integration test that:
- Created an `@Id` entity
- With a `ByteArray @Encrypt` field
- Saved it, reloaded it, and verified the decrypted value matched

If you use a specific feature combination for sensitive data, **write a test that validates the
encryption round-trip for that exact combination.** Don't assume the framework tested it.

### 2. When a framework adds a new capability, re-validate your assumptions
1.0.4 added `@Id` + `@Encrypt`. If you started using it, that was the moment to verify it worked
end-to-end for your specific fields — not just "it compiles and saves."

### 3. Treat encryption failures as silent
Incorrect encryption does not throw exceptions. It silently produces ciphertext that decrypts with
the wrong key. **You will not notice unless you test it explicitly.** This is true of every encryption
library, not just Encryptable.

### 4. Monitor your changelog
When a framework you depend on for data safety ships a security fix, read it carefully and check
whether it affects your usage. The 1.0.8 changelog was explicit about what was affected and provided
a migration path. Projects that maintain changelogs with this level of detail make it possible for
you to act quickly.

### 5. Defense in depth
Encryptable is one layer of your security posture. Transport encryption (TLS), database access
controls, secret management, audit logging, and infrastructure hardening are all additional layers
that reduce the blast radius if any single layer has a bug.

---

## How It Was Caught

The bug was **self-discovered** during the 1.0.8 storage abstraction refactoring — not reported
by a user, not found by an external researcher.

Specifically: all `ByteArray` field logic that previously lived inside `EncryptableByteFieldAspect`
was being moved into the new `StorageHandler` class. Rewriting that logic from scratch, with fresh
eyes, made the entity secret being used directly — instead of going through `getSecretFor()` —
immediately apparent. It could not be copy-pasted past without noticing it was wrong.

This matters: **the bug was caught by the same discipline that caused it** — careful, attentive
work on the codebase. It was not luck, and it was not someone else finding it first. The
refactoring that exposed the bug is the same refactoring that fixed it.

---

## Why the Test Suite Could Not Catch This

Encryptable's test suite is split into two categories:

**Cryptographic primitive tests** (`CryptoPropertiesTest`) — these verify that the raw `AES256`
and `HKDF` utilities behave correctly in isolation: round-trips succeed, IVs are unique, tampered
ciphertext is rejected, wrong secrets do not yield plaintext, and HKDF is deterministic and
namespace-isolated. These are unit-level tests with no MongoDB, no entities, and no framework
involvement.

**Integration tests** — every other test runs against a real embedded MongoDB instance, saves and
reloads real entities, and verifies end-to-end results.

The primitive tests are valuable and correct. But neither category covers the open gap — and this
is exactly why the bug went undetected.

The integration test for an `@Id` entity with a `ByteArray @Encrypt` field would:

1. Save the entity — the field is encrypted with the **wrong key** (the CID)
2. Reload the entity — the field is decrypted with the **same wrong key** (the CID)
3. Assert that the decrypted value matches the original — ✅ **passes**

Because the same wrong key was used consistently for both encrypt and decrypt within the same
test run, the round-trip always succeeded. The test could not observe that the key was wrong —
it only saw that the data came back intact, which it did.

This is a fundamental property of symmetric encryption: **a wrong key used consistently is
indistinguishable from the right key**, as long as you never try to decrypt with the correct key.
The bug was invisible to any test that only checked "does the value survive a save-reload cycle?"

The only test that would have caught it is one that:
- Inspects the raw ciphertext stored in MongoDB
- Attempts to decrypt it using the **master secret** and asserts success
- Or alternatively, attempts to decrypt using the CID and asserts **failure**

Neither of those tests exist — and `CryptoPropertiesTest` does not fill this gap. It verifies that
`AES256.decrypt(wrongSecret, ...)` returns the encrypted payload instead of plaintext, which is
correct. But it never asks *"is Encryptable passing the right secret to `AES256` in the first place?"*
That question can only be answered at the framework level: by inspecting raw stored bytes and
verifying they decrypt with the expected key.

**`EncryptableKeyCorrectnessTest` closes this gap.** After `save()`, the entity's inline ByteArray
field is left encrypted in memory. The test reads the raw ciphertext directly via reflection —
bypassing the framework's decrypt path entirely — and then manually calls `AES256.decrypt` with:

1. The **correct** key — asserts the original plaintext is recovered.
2. The **wrong** key — asserts the encrypted payload is returned unchanged.

For `@HKDFId` entities: correct key = entity secret, wrong key = master secret.
For `@Id` entities: correct key = master secret, wrong key = CID.

This is the exact regression check that would have caught the 1.0.4–1.0.7 bug on day one.
If the framework ever regresses to encrypting with the wrong key, assertion (1) will fail
because decrypting with the correct key will produce garbage instead of the original plaintext.

Encryptable's test suite still does not attempt to decrypt unencrypted entities to verify that
encryption is actually being applied — that remains a known gap. But **key-selection correctness
for all sensitive entity/field combinations is now explicitly verified**.

This is the precise scenario described in **"What This Should Teach You" §1** above: even with
a thorough integration test suite, an encryption bug that uses the wrong key consistently will
pass every round-trip test. **Explicit key-correctness tests are necessary** to catch this class
of bug — and `EncryptableKeyCorrectnessTest` now provides exactly that.

---

## What Encryptable Did in Response

1. **Self-discovered** the root cause while migrating `ByteArray` logic from `EncryptableByteFieldAspect` into `StorageHandler` (1.0.8)
2. **Fixed the bug** by ensuring `StorageHandler` calls `metadata.strategies.getSecretFor(encryptable)` throughout, replacing the direct entity secret usage in the old aspect
3. **Wrote a migration** (`Migration107to108`) that automatically detects and re-encrypts affected
   fields on first run with `encryptable.migration=true`
4. **Documented it openly** in the CHANGELOG with clear instructions for affected users
5. **Added `EncryptableKeyCorrectnessTest`** (1.0.9) — a dedicated regression test that bypasses
   the framework's decrypt path entirely, reads raw ciphertext directly from memory (via reflection
   for inline fields) and from storage (via `StorageHandler`), and manually verifies that the correct
   key decrypts successfully and the wrong key does not — for every field type (`ByteArray`, `String`,
   `List<String>`) and ID strategy (`@HKDFId`, `@Id`) combination. This makes it impossible for a
   wrong-key regression to hide behind a passing round-trip test. As a consequence, a security
   auditor no longer needs to manually trace callsites to verify key selection — if any callsite
   passed the wrong key, at least one of these tests would have failed.

The migration worked correctly on the first attempt — and that illustrates something important:
the bug was a **mechanical miss**, not a gap in understanding. Writing a correct migration
requires knowing exactly what was stored incorrectly, exactly what the correct value should be,
and exactly how to detect and transform one into the other.
That knowledge was never missing — only one callsite was.

The migration was the most important part. A fix without a migration would have left existing data
in a broken state and forced users to figure out recovery on their own.

---

## A Personal Note

I (WanionCane) am the sole author of this framework, and I made this mistake.

I know this is not something that should ever happen in a security framework.  
I built this framework precisely because I care deeply about data safety.  
That's what makes it sting.

When I introduced master secret support in 1.0.4, I updated every encryption path I had in mind, and I genuinely missed the `ByteArray` one.
It was not intentional. It was not indifference. It was a human error during a refactoring that introduced a new concept to the codebase, the same kind of error that has hit every non-trivial software project in history, including ones with entire security teams behind them.

I caught it myself while refactoring the storage implementation in 1.0.8. No user was burned by it, no external researcher had to find it for me. But I know that's not the point, it should never have shipped in the first place, and I own that completely.

What I can stand behind is how it was handled: it was caught, fixed, migrated, and documented openly, without downplaying what it was. No quiet patch, no vague release note. A clear description of exactly what went wrong, who was affected, and what to do about it.

If you are building security-sensitive software, whether as a framework author or an application developer, please let this be a reminder: **data safety is not a feature you implement once. It is a discipline you maintain continuously, with tests, reviews, changelogs, and humility.**

And sometimes, despite all of that, you still miss something. The measure of a project is not that it never makes mistakes, it is that it faces them honestly when they happen.

---

## Related Documents

- [CHANGELOG.md](../CHANGELOG.md) — 1.0.4 and 1.0.8 entries for full context
- [MIGRATING_FROM_OTHER_VERSIONS.md](MIGRATING_FROM_OTHER_VERSIONS.md) — Migration instructions
- [LIMITATIONS.md](LIMITATIONS.md) — Known limitations and constraints
- [AI_SECURITY_AUDIT.md](AI_SECURITY_AUDIT.md) — Security analysis of the framework
- [HKDFID_VS_ID.md](HKDFID_VS_ID.md) — When to use `@HKDFId` vs `@Id`

