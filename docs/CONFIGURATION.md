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
  - `0.34` (34% of available processors)

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
  - The default (34%) is chosen to avoid saturating the CPU, leaving resources for other application tasks.
  - On CPUs with SMT (Simultaneous Multithreading), 34% of logical cores is about 2/3 of physical cores, providing a safe and efficient cap for CPU-bound workloads.

---

### 2. `encryptable.gridfs.threshold`

- **Purpose:**
  - Sets the minimum size (in bytes) for a ByteArray field to be stored in MongoDB GridFS instead of the main document.
  - Prevents exceeding MongoDB's BSON document size limit and optimizes binary data handling.

- **Default Value:**
  - `1024` bytes

- **How It Works:**
  - Any ByteArray field larger than this threshold is stored in GridFS.
  - Values below 1024 are automatically raised to 1024 to avoid misconfiguration.

- **Example:**
  ```properties
  encryptable.gridfs.threshold=4096
  ```

- **Why It Matters:**
  - MongoDB documents max out at 16MB. GridFS storage for large fields prevents errors and improves performance.
  - The default balances efficiency and safety for most use cases.

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

## 🛠️ How to Configure

Set values in your environment, properties files, or system properties. For Spring Boot, use:

```properties
thread.limit.percentage=0.5
encryptable.gridfs.threshold=2048
encryptable.integrity.check=true
```

Or as environment variables:

```shell
set THREAD_LIMIT_PERCENTAGE=0.5
set ENCRYPTABLE_GRIDFS_THRESHOLD=2048
set ENCRYPTABLE_INTEGRITY_CHECK=true
```

---

## ✅ Best Practices & Recommendations

- **Thread Limit Percentage:**
  - Use the default unless you have specific concurrency needs or want to optimize for your hardware.
  - Avoid setting above `1.0` to prevent resource contention.
  - For I/O-bound tasks, consider using virtual threads by disabling the limit in Limited utility methods.

- **GridFS Threshold:**
  - Use the default unless you have specific binary storage needs.
  - Never set below 1024 bytes.

- **Integrity Check:**
  - Keep enabled for privacy-sensitive, regulated, or security-critical applications.
  - Disable only for extreme performance needs, and ensure alternative consistency checks are in place.

---

## 📊 Summary Table

| Property                      | Default | Description                                              |
|-------------------------------|---------|----------------------------------------------------------|
| thread.limit.percentage       | 0.34    | % of CPU cores for parallel Encryptable operations        |
| encryptable.gridfs.threshold  | 1024    | Minimum size (bytes) for GridFS storage                  |
| encryptable.integrity.check   | true    | Enable/disable integrity checks on entity access         |

---

## 🧭 Design Philosophy

Encryptable’s configuration defaults are chosen to maximize security, reliability, and developer convenience. The framework is security-first, but always flexible:

- **Safe by Default:** Integrity checks are enabled to prevent inconsistencies.
- **Efficient:** GridFS threshold and thread limit are set to optimize storage and system load.
- **Adaptable:** All options are runtime-configurable for any workload or risk profile.
- **Resource-Aware:** The Limited utility methods use thread.limit.percentage to ensure parallelism is safe and predictable for all workloads.

---

## 📚 Further Reading

- See [INNOVATIONS.md](INNOVATIONS.md) for a deep dive into Encryptable’s unique features and design choices.
- For advanced configuration and integration, consult the main [README.md].
- See [Limited.kt](/src/main/kotlin/tech/wanion/encryptable/util/Limited.kt) for details on parallel utility methods and thread management.

---

*Last updated: 2025-11-14*
