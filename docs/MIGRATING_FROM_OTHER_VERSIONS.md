# Migrating from Other Versions

This guide helps you upgrade between different versions of Encryptable.

---

## Current Version: 1.2.0

---

## Semantic Versioning Policy

Encryptable follows [Semantic Versioning 2.0.0](https://semver.org/):

- **Patch versions (1.0.x)** - No migration needed unless explicitly stated, drop-in replacement
- **Minor versions (1.x.0)** - Backward compatible features, or Spring Boot updates that may cause breaking changes unrelated to Encryptable's cryptography
- **Major versions (x.0.0)** - Breaking changes to Encryptable's core API or cryptographic architecture

**Minor version upgrades (1.1.0, 1.2.0, etc.)** may include:
- New features and enhancements to Encryptable
- Spring Boot version updates that could introduce breaking changes to your application (not Encryptable itself)
- These updates are always documented with clear migration notes

**Major version upgrades (2.0.0, 3.0.0, etc.)** will include:
- ✅ Detailed migration guide with step-by-step instructions
- ✅ Deprecation warnings in prior minor versions when possible
- ✅ Code examples showing before/after patterns
- ✅ Automated migration tools when feasible
- ✅ Clear timeline and support policy for older versions

> **Note:** Due to Encryptable's stable cryptographic architecture and Transient-knowledge design, it's highly unlikely that breaking changes significant enough to warrant a major version bump (2.0.0+) will occur.\
> The core framework is intentionally designed for long-term stability.\
> Most evolution will happen through backward-compatible minor releases (1.x.0).

---

## Data Safety

**Important:** Encryptable uses cryptographic addressing and deterministic encryption. When upgrading:

⚠️ **Always backup your MongoDB database before upgrading**

While we strive for backward compatibility, cryptographic systems require extra caution:
- Test upgrades in a staging environment first
- Verify data accessibility after upgrade
- Keep a backup of the previous Encryptable version JAR

### Rollback Procedure

If you need to rollback after an upgrade:

1. Stop your application
2. Restore MongoDB backup (if data was migrated)
3. Revert to the previous Encryptable version in `build.gradle.kts`:
   ```kotlin
   implementation("tech.wanion:encryptable-starter:1.0.7") // or your previous version
   ```
4. Rebuild and restart

---

## Version-Specific Migrations

---

### ⛔ Migrating to 1.1.0 (from 1.0.9 or earlier)

> **Why is this not 2.0.0?**
> The HKDF context key derivation change is a cryptographic breaking change — it invalidates all
> previously derived keys, CIDs, and encrypted values, making it incompatible with every prior
> version. Under strict Semantic Versioning, this warrants a major version bump (2.0.0).
>
> However, Encryptable has no known production deployments at this point in time. Bumping to 2.0.0
> for a project with no real users would create unnecessary noise and complicate the version history
> for the future adopters this project is aimed at. The decision was made to absorb the breaking
> change into 1.1.0 while the project is still in its early adoption phase, and document it
> explicitly here so the intent is clear.
>
> If you *are* running Encryptable in production and were not consulted about this — please open an
> issue. This note exists precisely to be transparent about that decision.

#### ⛔ There is no migration path from 1.0.x to 1.1.0.

Version 1.1.0 is **fully incompatible** with all prior versions. The HKDF context key derivation
change invalidates every derived key, CID, and encrypted value that was ever stored by 1.0.x. There
is no automated or manual migration that can convert existing data to the new scheme.

**If you are upgrading from 1.0.x:**
- Your existing encrypted data **cannot be read** by 1.1.0.
- There is no migration tool, and none is planned.
- The only path forward is to start fresh with a clean database.

**What changed:**
1. **Breaking: BSON Binary subtype** — CID values were previously stored as BSON Binary subtype
   `0x04` (UUID); they are now stored as custom subtype `128` (user-defined). Subtype 128 is
   reserved by MongoDB for custom applications and allows tools to distinguish CID data from
   standard UUID data. A mechanical re-encoding of the subtype would technically be possible in
   isolation, but because the HKDF context key derivation also changed in this release, all
   derived keys and CIDs are already invalidated regardless. Providing a partial migration that
   fixes the subtype while leaving the key derivation broken would be misleading — so no migration
   is offered.
2. **Breaking: HKDF context key derivation** — The class name passed as HKDF context was changed
   from `source.toString()` (e.g., `"class java.lang.String"`) to `source.name`
   (e.g., `"java.lang.String"`). Every derived key, every CID, and every encrypted field is
   affected.
3. **Storage threshold minimum lowered to 1KB** — The default remains 16KB. Applications using
   S3-compatible backends can now explicitly set `encryptable.storage.threshold=1024` to route
   binary fields to external storage from 1KB. No action required if using the default.

---

### ✅ Migrating to 1.2.0 from 1.1.0

**Release date:** 2026-03-28  
**Migration required:** Yes — if you have existing data with `@Sliced` fields created before 1.2.0

#### What changed

- **@Sliced Reference Header Format:** The internal reference header for `@Sliced` `ByteArray` fields now uses an 8-byte `Long` size prefix (was 4-byte `Int`). This enables support for extremely large files and aligns with other storage conventions. See the [Changelog](../CHANGELOG.md#111---2026-03-28) for details.
- **Spring Boot Update:** Upgraded from 4.0.3 to 4.0.5. No breaking changes expected, but review Spring Boot's release notes if you have custom integrations.

#### Who needs the migration

- Anyone with existing data using `@Sliced` fields created with Encryptable 1.1.0 or earlier. New data will always use the new format.

#### Step-by-step migration

**Step 1 — Back up your database**
```bash
mongodump --out ./backup-before-1.2.0-migration
```

**Step 2 — Update the dependency**
```kotlin
// build.gradle.kts
implementation("tech.wanion:encryptable-starter:1.2.2")
```

**Step 3 — Enable migration mode and start the application once**
```properties
encryptable.migration=true
```
Start your application and **wait for the migration to complete**. Watch the logs for:
```
Migration 1.1.0 → 1.2.0 completed.
```

> ⚠️ **Do not stop or restart the application while migration is in progress.**
> If interrupted, restore from the backup in Step 1 and start over.

**Step 4 — Disable migration mode**
```properties
encryptable.migration=false
```

**Step 5 — Verify**  
Load a few entities with `@Sliced` fields and confirm they are accessible and correct.

#### Impact if not migrated

- Applications reading `@Sliced` fields from databases created with Encryptable 1.1.0 or earlier **must** run the migration before upgrading to 1.2.0. Failure to migrate will result in deserialization errors or incorrect file sizes for affected fields.
- 1.2.2 maintains full compatibility with the 1.2.0 data format — no additional migration required for 1.2.0 → 1.2.2 upgrades.

#### Reference
- See the [Changelog](../CHANGELOG.md#111---2026-03-28) for a summary of changes.
- See the [migration utility documentation](link-to-migration-utility) for advanced usage.

---

### ✅ Migrating to 1.0.9 from 1.0.8 (or earlier 1.0.0–1.0.8)

**Release date:** 2026-03-07\
**Migration required:** Conditional — **only if you have `@Id` entities with both `List<Encryptable>` or single `Encryptable` fields AND at least one `@Encrypt` field**

#### What changed

**Security fix — `@Id` + `@Encrypt` + nested `Encryptable` references in `encryptableListFieldMap` / `encryptableFieldMap`**

In versions 1.0.0–1.0.8, `processFields` contained a code path that encrypted all values in
`encryptableListFieldMap` and `encryptableFieldMap` without first checking whether the parent entity
was cryptographically isolated (`@HKDFId`) or not (`@Id`).

For **`@Id` (non-isolated)** parent entities, those map values should always be stored as **plaintext
child IDs** — they are reference pointers, not secrets, and must never be encrypted.

However, if the `@Id` parent entity also declared at least one `@Encrypt` field
(`metadata.encryptable == true`), the old `processFields` pass would encrypt those plaintext IDs
with the master secret before persisting the document. On next load, the framework would attempt to
decrypt them, fail (because the values are not encrypted on the 1.0.9+ path), and the entity would
be marked as errored — preventing further updates and effectively losing the nested-entity references.

`@Id` entities that had **no** `@Encrypt` fields were unaffected: the early-return guard
`if (!metadata.encryptable) return` prevented the encrypt block from running, leaving values in
plaintext (correct).

In practice, `EncryptableList` always populated `encryptableListFieldMap` with the correct plaintext
ID before `processFields` ran, so the bug was immediately overwritten by the correct value on each
save — making the actual impact minimal. Nevertheless, the migration corrects any documents that may
have been written with the encrypted form.

> If you have **no** `@Id` entities that simultaneously have `List<Encryptable>` (or single
> `Encryptable`) fields **and** at least one `@Encrypt` field, you are **not affected**. The
> migration is safe to run regardless — it is a no-op for unaffected entities.

#### Step-by-step migration

**Step 1 — Back up your database**
```bash
mongodump --out ./backup-before-1.0.9
```

**Step 2 — Update the dependency**
```kotlin
// build.gradle.kts
implementation("tech.wanion:encryptable-starter:1.0.9")
```

**Step 3 — Enable migration mode and start the application once**
```properties
encryptable.migration=true
```
Start your application and **wait for the migration to complete**. Watch the logs for:
```
Migration 1.0.8 → 1.0.9 completed.
```

> ⚠️ **Do not stop or restart the application while migration is in progress.**\
> If interrupted, restore from the backup in Step 1 and start over.

**Step 4 — Disable migration mode**
```properties
encryptable.migration=false
```

**Step 5 — Verify**\
Load a few entities that have nested `Encryptable` fields and confirm their references are intact.

#### Who needs the data migration

Only entities matching **all three** of the following:
- Annotated with `@Id` (not `@HKDFId`)
- Have at least one `List<Encryptable>` or single `Encryptable` field
- Have at least one field annotated with `@Encrypt`

`@HKDFId` entities are **not affected**.

---

### ✅ Migrating to 1.0.8 from 1.0.7 (or earlier 1.0.4–1.0.7)

**Release date:** 2026-02-27\
**Migration required:** Yes — **mandatory on first run**

#### What changed

1. **Critical security fix — `@Id` + `@Encrypt` + `ByteArray` fields**\
   In versions 1.0.4–1.0.7, `ByteArray` fields annotated with `@Encrypt` on `@Id` entities were
   being encrypted with the entity's own secret (its public CID) instead of the master secret.\
   This was a missed callsite during the 1.0.4 refactoring that introduced master secret support.\
   See [MISSED_CALLSITE_BUG_1_0_8.md](MISSED_CALLSITE_BUG_1_0_8.md) for the full post-mortem.
   > If you were **only** using `@HKDFId` entities, or your `@Id` entities had no `ByteArray @Encrypt`
   > fields, you are **not affected** by this security fix. The migration is still needed
   > for the schema rename below.

2. **Schema rename — `gridFsFields` → `storageFields`**\
   The internal document field that tracks which fields are stored in the storage backend was
   renamed from `gridFsFields` to `storageFields` to reflect the new multi-backend storage abstraction.

3. **Configuration property rename**\
   `encryptable.gridfs.threshold` → `encryptable.storage.threshold`

#### Step-by-step migration

**Step 1 — Back up your database**
```bash
mongodump --out ./backup-before-1.0.8
```
Do not skip this. If the migration is interrupted, you will need this backup.

**Step 2 — Update the dependency**
```kotlin
// build.gradle.kts
implementation("tech.wanion:encryptable-starter:1.0.8")
```

**Step 3 — Rename the configuration property** (if you had it set)
```properties
# Before
encryptable.gridfs.threshold=2048

# After
encryptable.storage.threshold=2048
```

**Step 4 — Enable migration mode and start the application once**
```properties
encryptable.migration=true
```
Start your application and **wait for the migration to complete**. Watch the logs for confirmation:
```
Migration 1.0.7 → 1.0.8 completed successfully.
```

> ⚠️ **Do not stop or restart the application while migration is in progress.**\
> Interrupting mid-migration can result in partial updates and a **very high risk of data corruption**.\
> If it is interrupted, restore from the backup in Step 1 and start over.

**Step 5 — Disable migration mode**\
After the logs confirm completion, set the property back:
```properties
encryptable.migration=false
```
Or remove it entirely (it defaults to `false`).

**Step 6 — Verify**\
Load a few entities and confirm their encrypted `ByteArray` fields decrypt correctly.

#### Who needs the security re-encryption

Only entities matching **all three** of the following:
- Annotated with `@Id` (not `@HKDFId`)
- Have at least one `ByteArray` field annotated with `@Encrypt`
- Were saved while running versions 1.0.4, 1.0.5, 1.0.6, or 1.0.7

`@HKDFId` entities are **not affected** — their encryption was always correct.

---

### ✅ Migrating to 1.0.7 from 1.0.6 (or earlier)

**Release date:** 2026-01-30\
**Migration required:** No — drop-in upgrade

#### What changed

- **Polymorphic relationships support** — A single `Encryptable` field can now reference multiple
  entity types transparently. The framework automatically detects and persists the concrete type,
  and restores the correct type on load — with zero annotations or configuration required.

No data migration, no schema changes, no action needed. Simply update the dependency.

---

### ✅ Migrating to 1.0.5 from 1.0.4 (or earlier)

**Release date:** 2026-01-18\
**Migration required:** Conditional — **only if you relied on `createdByIP` or `createdAt`**

#### What changed

The `createdByIP` and `createdAt` fields were **removed** from the `Encryptable` base class.\
These fields were opt-out cross-reference leak vectors: `createdByIP` could correlate entities
created by the same IP, and `createdAt` could correlate entities by creation time.\
They are now **opt-in** on your own entity classes, and can now be encrypted with `@Encrypt`.

#### If you were not using these fields

No action needed. Drop-in upgrade.

#### If you were relying on these fields

Add them back to your entity classes. You can restore the exact previous behavior:

```kotlin
class MyEntity : Encryptable<MyEntity>() {
    @HKDFId override var id: CID? = null

    // Exact same behavior as before (plaintext, auto-populated)
    var createdByIP: String = EncryptableContext.getRequestIP()
    var createdAt: Instant = Instant.now()
}
```

Or take the opportunity to encrypt them:

```kotlin
    // New: encrypt for additional privacy
    @Encrypt var createdByIP: String = EncryptableContext.getRequestIP()
    @Encrypt var createdAt: Instant = Instant.now()
```

> **Note:** Existing documents in MongoDB that have `createdByIP` or `createdAt` fields will
> retain those fields in the database — MongoDB does not remove fields automatically. They will
> simply no longer be populated on new entities unless you add them back yourself.

---

### ✅ Migrating to 1.0.4 from 1.0.3 (or earlier)

**Release date:** 2026-01-07\
**Migration required:** Automatic (transparent, no action needed)

#### What changed

- `@Id` entities can now use `@Encrypt` fields, requiring a **master secret**.
- References from `@Id` entities to `@HKDFId` entities changed from storing the child's secret
  to storing the child's CID only, preventing secret leakage if the master secret is compromised.

#### Steps

1. Update the dependency to 1.0.4+.
2. If you use `@Id` entities with `@Encrypt` fields, configure the master secret:
   ```properties
   encryptable.master.secret=your-high-entropy-master-secret
   ```
3. Start the application. The reference format migration is **automatic and transparent** —
   it runs as entities are loaded and saved (lazy migration).

No manual intervention is required.

---

### Migrating to 1.0.3 and below

Versions 1.0.0 through 1.0.3 are drop-in replacements for each other.\
No migration steps are required when moving between these versions.

---


### Migrating to 2.0.0 (Future)
*Migration guide will be added when 2.0.0 is released.*

---

## Stay Informed

- Watch the [GitHub repository](https://github.com/WanionTechnologies/Encryptable) for releases
- Check the [Changelog](../CHANGELOG.md) for version-specific changes
- Review [GitHub Releases](https://github.com/WanionTechnologies/Encryptable/releases) for announcements

---

## Migration Support

If you encounter issues upgrading Encryptable:
1. Check the version-specific migration guide above
2. Review the [Changelog](../CHANGELOG.md)
3. Search [existing issues](https://github.com/WanionTechnologies/Encryptable/issues)
4. Open a [new issue](https://github.com/WanionTechnologies/Encryptable/issues/new) with:
   - Current version
   - Target version
   - Error messages or unexpected behavior
   - Minimal reproduction example

---

## Questions?

For migration questions or support, please open an issue on [GitHub](https://github.com/WanionTechnologies/Encryptable/issues).