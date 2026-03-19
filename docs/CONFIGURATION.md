# ⚙️ Encryptable Framework Configuration

**Comprehensive Guide to Runtime Settings & Defaults**

> _"Configuration is the foundation of security and usability.\
> Encryptable empowers developers to tune privacy, performance, and reliability for every deployment scenario."_

---

## 🗂️ Overview

Encryptable is engineered for flexibility.\
All core behaviors can be adjusted at runtime using environment variables, application properties, or system properties.\
This guide details every configurable option, its default, rationale, and impact—so you can make informed decisions for your project.

---

## 🔧 Configuration Options

### 1. `thread.limit.percentage`

- **Purpose:**
  - Sets the percentage of available CPU cores to use for parallel operations (encryption, decryption, integrity checks, etc.).
  - Controls concurrency and resource usage for all parallel tasks in Encryptable, including the Limited utility methods.

- **Default Value:**
  - `0.38` (38% of available processors)

- **How It Works:**
  - The actual thread limit is calculated as: `max(1, availableProcessors * thread.limit.percentage)`.
  - Values above `1.0` are capped at `1.0` (100%).
  - Values below `0.01` are raised to at least one thread.
  - Used by all parallelForEach, parallelMap, parallelReplaceAll, and smartReplaceAll methods in Limited.kt to restrict concurrency for CPU-bound tasks.
  - For I/O-bound tasks, you can disable the limit and use virtual threads for maximum concurrency.

- **Example:**
  ```properties
  thread.limit.percentage=0.5
  ```

- **Why It Matters:**
  - Prevents resource exhaustion and thread starvation by capping concurrency for each request.
  - Ensures fair resource allocation and system stability, especially in multi-tenant or high-concurrency environments.
  - The default (38%) is chosen to avoid saturating the CPU, leaving resources for other application tasks.
  - On CPUs with SMT (Simultaneous Multithreading), 38% of logical cores is about 2/3 of physical cores, providing a safe and efficient cap for CPU-bound workloads.

---

### 2. `encryptable.storage.threshold`

- **Purpose:**
  - Sets the minimum size (in bytes) for a ByteArray field to be stored in an external storage backend (such as GridFS, S3, file system, or any custom storage) instead of the main document or entity.
  - Prevents exceeding document size limits and optimizes binary data handling for all supported storage backends.

- **Default Value:**
  - `16384` bytes (16KB)

- **How It Works:**
  - Any ByteArray field larger than this threshold is stored in the configured external storage backend.
  - When not configured, defaults to 16384 bytes (16KB).
  - When explicitly configured, the minimum accepted value is 1024 bytes (1KB) — values below 1024 are raised to 1024.

- **Example:**
  ```properties
  encryptable.storage.threshold=65536
  ```

- **Why It Matters:**
  - For databases with document size limits (e.g., MongoDB), external storage prevents errors and improves performance.
  - For other backends (S3, file system, etc.), it allows efficient handling of large binary data.
  - The default of 16KB is a safe, performant starting point for most applications — small fields stay inline and avoid unnecessary storage round-trips.
  - For applications using a cost-efficient external storage backend (S3, Cloudflare R2, Backblaze B2, etc.), lowering the threshold to 1KB is a deliberate cost decision: at scale, database storage is orders of magnitude more expensive per GB than object storage. Routing binary fields to cheap object storage from 1KB onwards keeps database costs flat regardless of entity volume.
  > ⚠️ **Lowering the threshold does not benefit GridFS users.** GridFS is still MongoDB — data stored in GridFS counts against your MongoDB storage quota at the same rate as any other document data. Only lower the threshold if you are using a genuinely external, cheaper storage backend.

---

### 3. `encryptable.integrity.check`

- **Purpose:**
  - Enables or disables automatic integrity checks on Encryptable entities when accessed.
  - Ensures all references are valid and cleans up missing or orphaned entries.

- **Default Value:**
  - `true`

- **How It Works:**
  - If `true`, every entity access triggers a reference integrity check and cleanup.
  - If `false`, checks are skipped for performance, but stale references may persist.

- **Example:**
  ```properties
  encryptable.integrity.check=false
  ```

- **Why It Matters:**
  - Integrity checks are vital for privacy, security, and data consistency.
  - Disabling may be suitable for high-throughput scenarios, but is not recommended for sensitive data.

---

### 4. `encryptable.migration`

- **Purpose:**
  - Indicates whether the application is currently performing a migration process (schema or data migration).
  - Can be used to conditionally disable certain features or checks during migration for performance or compatibility reasons.

- **Default Value:**
  - `false`

- **How It Works:**
  - When set to `true`, Encryptable may skip certain checks or enable migration-specific logic.
  - Should only be enabled during an active migration process.

- **Example:**
  ```properties
  encryptable.migration=true
  ```

- **Why It Matters:**
  - Prevents accidental interference with normal operations during migration.
  - Ensures that migration logic is only active when explicitly required.
  - Reduces risk of data inconsistency or performance issues during migration.

---

### 5. `encryptable.cid.base64`

- **Purpose:**
  - Controls how `CID.toString()` renders a CID value.
  - When `true` (default), CIDs are rendered as **standard Base64 with padding** — the same format MongoDB Compass displays for BSON Binary subtype `0x03` fields.
  - When `false`, CIDs are rendered as **URL-safe Base64 without padding** (22 characters) — the native CID format.

- **Default Value:**
  - `true`

- **How It Works:**
  - Affects every place a CID is converted to a string (logging, error messages, serialization, etc.).
  - MongoDB Compass displays Binary subtype `0x03` values as standard Base64 with `=` padding. With this option enabled, the string you see in logs is directly copy/pasteable into Compass and vice versa.
  - Set to `false` if you rely on the shorter, URL-safe 22-character format in your application (URLs, QR codes, external APIs, etc.).

- **Example:**
  ```properties
  encryptable.cid.base64=false
  ```

- **Why It Matters:**
  - Eliminates the friction of mentally converting between two Base64 variants when debugging with MongoDB Compass.
  - The default of `true` is chosen for developer convenience — the standard Base64 format is what you see in your database tool, so your logs match your database view out of the box.
  - If you expose CIDs in URLs or APIs, set to `false` to get the compact, URL-safe representation.

---

## 🛠️ How to Configure

Set values in your environment, properties files, or system properties. For Spring Boot, use:

```properties
thread.limit.percentage=0.5
encryptable.storage.threshold=1024
encryptable.integrity.check=true
encryptable.migration=false
encryptable.cid.base64=true
```

Or as environment variables:

```shell
set THREAD_LIMIT_PERCENTAGE=0.5
set ENCRYPTABLE_STORAGE_THRESHOLD=1024
set ENCRYPTABLE_INTEGRITY_CHECK=true
set ENCRYPTABLE_MIGRATION=false
set ENCRYPTABLE_CID_BASE64=true
```

---

## ✅ Best Practices & Recommendations

- **Thread Limit Percentage:**
  - Use the default unless you have specific concurrency needs or want to optimize for your hardware.
  - Avoid setting above `1.0` to prevent resource contention.
  - For I/O-bound tasks, consider using virtual threads by disabling the limit in Limited utility methods.

- **Storage Threshold:**
  - Use the default (16KB) unless you have specific binary storage needs.
  - Can be lowered to a minimum of 1KB for S3-compatible backends where cost savings are meaningful.
  - Do not lower below 1KB — values below 1024 are automatically raised to 1024.
  - Never lower the threshold when using GridFS — it will not reduce costs and will add unnecessary storage round-trips.

- **Integrity Check:**
  - Keep enabled for privacy-sensitive, regulated, or security-critical applications.
  - Disable only for extreme performance needs, and ensure alternative consistency checks are in place.

- **Migration:**
  - Keep disabled under normal operations to prevent unintended behavior.
  - Enable only during explicit migration processes, and disable immediately after.

- **CID Base64:**
  - Keep the default (`true`) for the best debugging experience with MongoDB Compass — CIDs in your logs will match exactly what you see in the database tool.
  - Set to `false` only if you rely on the compact, URL-safe 22-character format in URLs, QR codes, or external APIs.

---

## 📊 Summary Table

| Property                    | Default | Description                                              |
|-----------------------------|---------|----------------------------------------------------------|
| thread.limit.percentage     | 0.38    | % of CPU cores for parallel Encryptable operations        |
| encryptable.storage.threshold    | 16384   | Minimum size (bytes) for external storage routing (min: 1024) |
| encryptable.integrity.check | true    | Enable/disable integrity checks on entity access         |
| encryptable.migration       | false   | Enable/disable migration mode for schema/data changes    |
| encryptable.cid.base64      | true    | Render CIDs as standard Base64 (Compass-compatible) or URL-safe Base64 |

---

## 🧭 Design Philosophy

Encryptable’s configuration defaults are chosen to maximize security, reliability, and developer convenience. The framework is security-first, but always flexible:

- **Safe by Default:** Integrity checks are enabled to prevent inconsistencies.
- **Efficient:** Storage threshold and thread limit are set to optimize storage and system load.
- **Adaptable:** All options are runtime-configurable for any workload or risk profile.
- **Resource-Aware:** The Limited utility methods use thread.limit.percentage to ensure parallelism is safe and predictable for all workloads.

---

## 📚 Further Reading

- See [INNOVATIONS.md](INNOVATIONS.md) for a deep dive into Encryptable’s unique features and design choices.
- For advanced configuration and integration, consult the main [README.md].
- See [Limited.kt](/src/main/kotlin/tech/wanion/encryptable/util/Limited.kt) for details on parallel utility methods and thread management.

---

# Migration Note: GridFS Threshold Property Renamed

If you previously used the property `encryptable.gridfs.threshold`, you must rename it to `encryptable.storage.threshold` in your configuration files (e.g., `application.properties`, `application.yml`, or environment variables). This change is required for compatibility with Encryptable 1.0.8 and later, as the framework now supports multiple storage backends, not just GridFS.

**Example:**

- Before:
  ```properties
  encryptable.gridfs.threshold=1024
  ```
- After:
  ```properties
  encryptable.storage.threshold=1024
  ```

Be sure to update all relevant configuration files and deployment environments.

---

*Last updated: 2026-03-02*
