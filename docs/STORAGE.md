# Encryptable: Storage Abstraction & Extensibility

Encryptable introduces a flexible and extensible storage abstraction layer, enabling seamless integration with various storage backends for large binary data. This document explains the motivation, design, and extensibility of the Storage system in Encryptable.

> 💡 **The field-as-live-mirror pattern introduced here is, to our knowledge, a novel combination not found in any other framework.** See [Innovations](INNOVATIONS.md) for the full technical analysis.

## 🚀 Why Storage Abstraction Exists

Modern applications often need to store large binary data (such as files, images, or documents) alongside structured entity data. Storing such data directly in the database can be inefficient or impractical, especially as data grows. Encryptable's storage abstraction solves this by:

- **Decoupling storage logic from encryption and entity management.**
- **Supporting multiple storage backends** (e.g., MongoDB GridFS, S3, file system, or custom solutions).
- **Enabling lazy loading and efficient cleanup of large binary fields.**
- **Allowing users to extend or replace storage mechanisms without modifying core framework logic.**
- **Reducing infrastructure cost at scale** — database storage is orders of magnitude more expensive per GB than object storage. Routing binary fields to external storage from 1KB onwards keeps database costs flat, even with millions of entities.
  > ⚠️ **This cost benefit does not apply to GridFS.** GridFS is still MongoDB — data stored in GridFS counts against your MongoDB storage quota and is billed at the same rate as any other MongoDB data. The cost savings only materialise when using a genuinely external and cheaper backend such as S3, Cloudflare R2, Backblaze B2, or any other object storage service. If cost is a concern, prefer an S3-compatible backend over GridFS.

## 🪞 The Field-as-Live-Mirror

This is the core concept of Encryptable's storage system — and what makes it unlike anything else in any framework.

A `ByteArray` field on an entity is a **live mirror of its storage backend.** The field value IS the storage state. The developer never interacts with a storage API directly.

```kotlin
class UserDocument : Encryptable<UserDocument>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var resume: ByteArray? = null  // No annotation needed for GridFS — threshold handles it.
}

// Storing — just assign:
entity.resume = pdfBytes
repository.save(entity)

// Updating — assign a new value. Old file deleted, new file stored atomically:
entity.resume = updatedPdfBytes
repository.save(entity)

// Deleting the file — assign null. Zero orphaned files:
entity.resume = null
repository.save(entity)

// Reading — lazily fetched and decrypted on first access:
val retrieved = repository.findBySecretOrNull(secret)!!
val pdf = retrieved.resume  // Fetched from storage, decrypted, returned.
```

**No storage API. No reference tracking. No cleanup code. Just a field.**

### What happens transparently:

| Action | What Encryptable does |
|---|---|
| Assign bytes above threshold | Encrypts (if `@Encrypt`), stores in backend, saves compact reference |
| Assign bytes below threshold | Stores inline in the document |
| Read the field | Lazily fetches from backend, decrypts, returns plaintext |
| Assign a new value | Creates new file **first**, then deletes old — atomic by design. In-place update is not possible: AES-256-GCM uses a random IV on every encryption, so the ciphertext is always entirely different — even for the same plaintext. There is nothing to "update". |
| Assign `null` | Deletes file from backend, zero orphaned files |
| Delete the entity | All associated storage files cleaned up automatically |

## 📦 Out-of-the-Box Storage: GridFSStorage

Encryptable provides a ready-to-use storage backend based on MongoDB GridFS, called `GridFSStorage`.

- **What is GridFSStorage?**
  - GridFS is MongoDB's specification for storing and retrieving large files, splitting them into smaller chunks for efficient storage and retrieval.
  - `GridFSStorage` integrates seamlessly with Encryptable, allowing you to store large binary data (such as files, images, or documents) without worrying about database size limits or performance issues.
- **Benefits:**
  - No additional setup required — works out of the box with MongoDB.
  - **No annotation needed** — any `ByteArray` field above the configured threshold (default: 1KB) is automatically routed to GridFS.
  - **The 1KB default is a cost decision:** at scale, database storage (MongoDB, PostgreSQL, etc.) is orders of magnitude more expensive per GB than object storage (S3, GridFS, R2, etc.). Storing millions of small binary fields inline in the database adds up quickly. Routing them to cheap object storage from 1KB onwards keeps database costs flat regardless of how many entities are stored.
  > ⚠️ **GridFS does not provide this cost benefit.** GridFS is a MongoDB feature — binary data stored in GridFS still resides in your MongoDB cluster and is billed at MongoDB storage rates, the same as any other document. The cost advantage of the 1KB threshold only applies when using a genuinely external object storage backend (S3, R2, Backblaze B2, Wasabi, etc.). GridFS is still the right choice for applications already on MongoDB that need large file support without adding another infrastructure dependency — but it is not a cost optimisation.
  - Supports lazy loading, efficient cleanup, and automatic management of large binary fields.
  - Ensures binary data is stored securely and efficiently alongside your encrypted entities.
- **When to use:**
  - Ideal for applications already using MongoDB and needing to store large files or binary data.
  - Use as the default storage backend, or as a reference implementation for building your own custom storage solutions.

You can always implement and register your own storage backend (such as Amazon S3, S3-compatible services like MinIO or Wasabi, or file system) if your application has different requirements. Custom backends require a single annotation on the field.

> 💡 **S3-compatible storage is expected to be the most commonly used custom backend.** Amazon S3, Cloudflare R2, MinIO, Wasabi, DigitalOcean Spaces, Backblaze B2, and Oracle OCI Object Storage all share the same S3-compatible API — meaning a single `S3StorageImpl` covers virtually every major cloud provider. See the [working example](../examples/storage/S3StorageImpl.kt) for a full implementation in under 80 lines.

## 🧑‍💻 Reference Implementations for Custom Storage

In addition to being the default storage backend, `GridFSStorage` serves as a practical template for implementing your own custom `IStorage` backends. Its code demonstrates:

- Minimal boilerplate and clear separation of concerns
- How to implement all required `IStorage` methods
- Proper use of Spring bean registration
- Integration with Encryptable's storage abstraction

**MemoryStorageImpl** is another example implementation, designed for testing and demonstration purposes. It provides an in-memory storage backend with minimal code, making it ideal for unit tests, development, or as a starting point for simple custom storage solutions.

**S3StorageImpl** (`examples/storage/S3StorageImpl.kt`) is a fully working example of an S3-compatible storage backend. It demonstrates how to integrate any S3-compatible service (Amazon S3, MinIO, Wasabi, DigitalOcean Spaces) in under 80 lines of code — proving just how straightforward the `IStorage` interface is to implement.

**Recommendation:**
If you are building a new storage backend (such as S3, file system, or a cloud provider), review `GridFSStorage`, `MemoryStorageImpl`, and `S3StorageImpl` in the source code as starting points. You can adapt their structure and patterns for your own needs.

## 🧩 How It Works — Internally

- **Storage is abstracted via the `IStorage` interface** — `create`, `read`, and `delete` are the only three operations any backend needs to implement.
- **Threshold-based routing** — fields below the threshold are stored inline in the document; fields above are routed to the storage backend automatically.
- **Custom backends** are selected via a field-level annotation linked to an `IStorage` implementation. If no annotation is present, GridFS is used by default.
- **AspectJ intercepts field reads** to trigger lazy loading — the storage backend is not contacted until the field is actually accessed.
- **Encryption and decryption happen in `StorageHandler`**, not in the `IStorage` implementation. The storage backend always receives and returns raw bytes — it never sees plaintext.
- **Atomic replace** — on update, the new file is created before the old one is deleted. If creation fails, the old data is untouched. In-place update is architecturally impossible: AES-256-GCM generates a new random IV on every encryption call, meaning the ciphertext is always entirely different — even if the plaintext is identical. Every write is inherently a new ciphertext, so replace is the only semantically correct operation.

## 🛠️ Creating Custom Storage Backends

Encryptable is designed for extensibility. You can implement your own storage backend (for example, Amazon S3, S3-compatible services, file system, or a cloud provider) by following these steps:

1. **Implement the `IStorage` interface**
   - Define how to store, retrieve, and delete binary data.
   - Implement the `referenceLength` property to return the byte size of a single reference for your backend (e.g., 12 for MongoDB ObjectId, 16 for CID, or any size your backend requires).
   - Annotate your implementation with `@Component` (or another Spring stereotype annotation) so Encryptable can retrieve it as a Spring bean.
   - Ensure your implementation is thread-safe and efficient for your use case.
   - Example:
     ```kotlin
     @Component
     class S3StorageImpl : IStorage<CID> {
         override val referenceLength: Int = 16  // CID is 16 bytes
         // ...rest of implementation...
     }
     ```
   > 💡 **`@Sliced` requires no changes to your `IStorage` implementation.** Any existing backend automatically supports sliced fields — the framework handles slicing entirely on its side using the `referenceLength` you already provide.
2. **Create a custom annotation for your storage**
   - Define an annotation (e.g., `@S3Storage`) and annotate it with `@Storage(storageClass = S3StorageImpl::class)`.
   - Example:
     ```kotlin
     @Target(AnnotationTarget.FIELD)
     @Retention(AnnotationRetention.RUNTIME)
     @Storage(storageClass = S3StorageImpl::class)
     annotation class S3Storage
     ```
3. **Annotate your entity field with your custom annotation**
   - Example:
     ```kotlin
     class MyEntity : Encryptable<MyEntity>() {
         @S3Storage
         var file: ByteArray? = null
     }
     ```
4. **Encryptable handles the rest**
   - The framework will automatically use your storage implementation for any field annotated with your custom annotation.
   - No additional configuration or boilerplate is required.

**(Optional) Add metadata or custom logic:**
- If your storage backend requires metadata (e.g., S3 object keys, URLs), you can extend the storage reference object as needed.

This approach allows you to plug in any storage backend with minimal effort, keeping your entity code clean and declarative.

## 🧑‍🔬 Example: Amazon S3-Compatible Storage Backend

Here is a practical example of integrating an Amazon S3 (or S3-compatible) storage backend with Encryptable. This demonstrates how straightforward it is to add new storage solutions:

1. **Implement the `IStorage` interface**
   - See `S3StorageImpl` for a fully working example (`examples/storage/S3StorageImpl.kt`):
     ```kotlin
     @Component
     class S3StorageImpl : IStorage<CID> {
         // ...implementation as shown in examples/storage/S3StorageImpl.kt...
     }
     ```
2. **Create a custom annotation for your storage**
   - Define an annotation and link it to your storage implementation:
     ```kotlin
     @Target(AnnotationTarget.FIELD)
     @Retention(AnnotationRetention.RUNTIME)
     @Storage(storageClass = S3StorageImpl::class)
     annotation class S3Storage
     ```
3. **Annotate your entity field**
   - Use your annotation to store a file in S3:
     ```kotlin
     class MyEntity : Encryptable<MyEntity>() {
         @S3Storage
         var file: ByteArray? = null
     }
     ```

**Result:** Encryptable will transparently use your S3 storage implementation for any field annotated with `@S3Storage`. No extra configuration or boilerplate is required. This approach works for any S3-compatible service (e.g., MinIO, Wasabi, DigitalOcean Spaces).

## 📎 Storage Reference Details

The storage reference object (the type parameter of `IStorage<ReferenceObj>`) is designed to be a compact, database-stored reference for retrieving the actual binary data and any associated metadata needed for file access.

- **Reference Size:**
  - Each `IStorage` implementation defines its own reference size by implementing the `referenceLength` property. This makes the framework backend-agnostic — a GridFS implementation may use a 12-byte ObjectId, an S3 implementation may use a 16-byte CID, and a custom backend may use a different size that fits its addressing scheme.
  - The reference is stored as a `ByteArray` directly in the entity document, keeping entity documents lightweight.
  - For `@Sliced` fields, the stored reference is `8 + referenceLength * N` bytes — an 8-byte big-endian `Long` containing the original plaintext length, followed by `N` concatenated slice references. No separate manifest needed.
- **Purpose:**
  - The reference is used to look up the actual file or binary data in the storage backend.
  - It can also be used to retrieve additional metadata (such as S3 object keys, file paths, or other identifiers) required for accessing or managing the file.
  - The reference may also point to a metadata entity in the database (for example, a document in a metadata collection, similar to how GridFS uses the `fs.files` collection to store file metadata). This allows for flexible and extensible metadata management, supporting advanced use cases where additional information about the file (such as size, content type, or custom attributes) is needed.
- **Extensibility:**
  - You can design your reference object to include any fields needed by your storage backend, as long as it remains compact and serializable.

This design keeps entity documents lightweight while enabling flexible and powerful storage integrations.

## 🔒 Encryption of ByteArrays

If you want your `ByteArray` fields to be encrypted, simply annotate them with `@Encrypt`:

```kotlin
class MyEntity : Encryptable<MyEntity>() {
    @Encrypt
    var file: ByteArray? = null
}
```

If the field is also annotated with a storage annotation (e.g., `@S3Storage`), Encryptable will automatically handle encryption and decryption for you. **Your storage implementation does not need to perform any encryption or decryption itself** — Encryptable ensures that all data is securely encrypted before being passed to storage, and decrypted when read back.

### Which secret is used for encryption?

The secret used to encrypt the `ByteArray` depends on the entity type:

| Entity type | Secret used |
|---|---|
| `@HKDFId` entity | The entity's own secret — the same one used for all other `@Encrypt` fields on that entity. Per-entity cryptographic isolation is fully preserved. |
| `@Id` entity | The Master Secret configured via `encryptable.master.secret` or `ENCRYPTABLE_MASTER_SECRET`. |

This means that for `@HKDFId` entities, even the stored binary data is cryptographically isolated per user — the storage backend holds ciphertext that only the entity's secret can decrypt. For `@Id` entities, the Master Secret is the encryption boundary, consistent with how all other `@Encrypt` fields on `@Id` entities behave.

> See [HKDFID_VS_ID.md](HKDFID_VS_ID.md) for a full comparison of the two entity types and their security models.

This separation of concerns keeps your storage code simple and focused only on storing and retrieving raw bytes, while Encryptable transparently manages all cryptographic operations.

## ⚠️ Handling Failures and Robustness

When implementing a custom storage backend, it is essential to prepare for potential failures such as network timeouts, partial writes, or storage unavailability. Your `IStorage` implementation should:

- Handle and propagate exceptions appropriately (e.g., IO/network errors, timeouts, permission issues).
- Ensure atomicity where possible — Encryptable's `StorageHandler` already guarantees atomic replace at the framework level (new file created before old file deleted), but your implementation should be resilient too.
- Implement retries or fallback logic if suitable for your storage backend.
- Clean up any temporary or incomplete data in case of errors.
- Log errors and provide meaningful feedback for troubleshooting.

Encryptable will call your storage methods as part of entity persistence. If a storage operation fails, the exception will propagate up to the application, so robust error handling is critical for data integrity and user experience.

## 🌐 Example Use Cases

- **Amazon S3 or S3-Compatible Storage** *(most common)* — Store large files in Amazon S3, Cloudflare R2, MinIO, Wasabi, DigitalOcean Spaces, Backblaze B2, or Oracle OCI Object Storage. A single `S3StorageImpl` covers all of them — they share the same API.
- **File System Storage:** Store files on disk, referencing them by path or unique identifier.
- **Hybrid Storage:** Use different storage backends for different entity types or fields on the same entity.

## 🔪 Sliced Storage (`@Sliced`)

A capability of Encryptable is **sliced storage** — where a large `ByteArray` field is automatically split into independently encrypted slices, each with its own IV and authentication tag, and stored as separate entries in the storage backend.

### The Motivation

The primary driver for this feature is **memory-constrained environments** such as Cloudflare Workers (128MB limit), AWS Lambda with low memory allocation, and other serverless or edge runtimes. A 2GB file stored as a single encrypted `ByteArray` is not just inefficient in these environments — it is **physically impossible to decrypt in one go**. The only viable path is independently encrypted slices that can be fetched and decrypted one at a time.

**Why slices solve this — precisely:**

Without `@Sliced`, loading a 2GB file requires:
1. Load the full 2GB ciphertext into memory
2. Decrypt it into another 2GB of plaintext
3. **~4GB peak memory.** Physically impossible in 128MB.

With `@Sliced(sizeMB = 4)`:
1. Fetch **one 4MB slice** (ciphertext) — ~4MB in memory
2. Decrypt it — ~8MB peak (ciphertext + plaintext simultaneously)
3. Process or forward the decrypted 4MB
4. **Discard both** — memory freed immediately
5. Repeat for the next slice

**Peak memory at any point: ~8MB — regardless of total file size.**

This is not an edge case. Any serverless or edge function environment with memory constraints faces the same limitation. The same benefit applies to standard JVM backends serving large file downloads — slicing keeps server memory flat regardless of file size or concurrent downloads.

### The API — `@Sliced`

The developer experience remains identical to a regular `ByteArray` field. The only change is a single annotation:

```kotlin
class UserDocument : Encryptable<UserDocument>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt          // optional — without this, slices are stored as raw bytes
    @Sliced(sizeMB = 4)  // 4MB slices
    var file: ByteArray? = null  // still a ByteArray — nothing changes for the developer
}
```

- **No API changes.** Assign, update, null, read — all work exactly as before.
- **`sizeMB` is configurable** — defaults to `4` (4MB), overridable per field.
- **Slicing is entirely transparent** — the developer never sees slices, never manages them, never thinks about them.
- **Compatible with any storage backend** — GridFS, S3, or custom. Each slice is stored as an independent entry.

### Recommended Slice Sizes

| Use case | Recommended `sizeMB` | Reason |
|---|---|---|
| **Large video / media** | `4` (4MB) | ~4ms decrypt time with AES-NI, fits edge memory budgets |
| **Audio files** | `1` (1MB) | Smaller slices, lower per-slice latency |
| **Large documents / CAD** | `8` (8MB) | Balance between round trips and memory |
| **Medical imaging (DICOM)** | `4` (4MB) | Large files, enables parallel fetch |
| **General large files** | `2` (2MB) | Safe default for unknown content |

**MongoDB Document Size Limit:**

When using MongoDB, the total size of a document is limited to 16MB. This means the reference header for a `@Sliced` field (8 bytes + N references) must fit within this limit. The maximum number of slices (N) is:

    maxSlices = floor((16MB - 8) / referenceLength)
    maxFileSize = maxSlices * sliceSize
 
For example, with a 16-byte reference and 4MB slices:

    maxSlices = floor((16 * 1024 * 1024 - 8) / 16) ≈ 1,048,575
    maxFileSize ≈ 1,048,575 * 4MB ≈ 4TB

For example, with a 16-byte reference and 32MB slices:

    maxSlices = floor((16 * 1024 * 1024 - 8) / 16) ≈ 1,048,575
    maxFileSize ≈ 1,048,575 * 32MB ≈ 33.5TB

This allows extremely large files to be referenced, as long as the reference array fits within the 16MB MongoDB document size limit.

**Note:** The calculations above are an intentional oversimplification. In practice, a MongoDB document will always contain additional metadata (such as BSON overhead, other fields), which further reduces the space available for the reference header. As a result, the true maximum file size you can reference will be somewhat lower than the theoretical maximum shown here. If you are approaching the document size limit, you should conservatively estimate the available space for the reference header and test with your actual document structure to ensure you do not exceed MongoDB's 16MB document size limit.

**Recommendation:**

When working with files that could exceed 2GB, always annotate the field with `@Sliced` and process the data in manageable slices. The Java platform imposes a strict 2GB limit on array sizes, so attempting to load or store larger files as a single `ByteArray` will fail with an `OutOfMemoryError` or `NegativeArraySizeException`.

**File larger than 2GB:**

For files larger than 2GB (`Int.MAX_VALUE`), the standard single-blob "backing field" (i.e., a `ByteArray` field) is not an option. You cannot assign or retrieve such files as a single array. Instead, you must:
- Manually construct the reference header (an 8-byte big-endian Long for the original length, followed by N slice references).
- Write and read the file in slices, using streams or chunked processing.
- When reading, parse the 8-byte header to determine the original length and number of slices, then fetch and process each slice sequentially or in parallel.

**Example: Writing a large file in slices**
```kotlin
val totalLength: Long = /* file size, e.g. from inputStream.available() or known metadata */
val sliceSize = 16 * 1024 * 1024 // 16MB
val sliceCount = (totalLength + sliceSize - 1) / sliceSize
val header = ByteBuffer.allocate(8).putLong(totalLength).array()
val reference = ByteArray(8 + sliceCount * storage.referenceLength)
System.arraycopy(header, 0, reference, 0, 8)
for (i in 0 until sliceCount) {
    val slice: ByteArray = /* read next chunk from input stream */
    val sliceRef = storage.create(slice)
    System.arraycopy(sliceRef, 0, reference, 8 + i * storage.referenceLength, storage.referenceLength)
}
// Store 'reference' as the field value
```

**Example: Reading a large file in slices**
```kotlin
val reference: ByteArray = /* field value from storage */
val totalLength = ByteBuffer.wrap(reference, 0, 8).long
val sliceCount = (reference.size - 8) / storage.referenceLength
for (i in 0 until sliceCount) {
    val offset = 8 + i * storage.referenceLength
    val sliceRef = reference.copyOfRange(offset, offset + storage.referenceLength)
    val slice = storage.read(sliceRef)
    // Process or stream 'slice' as needed
}
```

**Example: Deleting a large file in slices**
To delete a large file stored in slices, iterate over each slice reference and call the storage backend's delete method:
```kotlin
val reference: ByteArray = /* field value from storage */
val sliceCount = (reference.size - 8) / storage.referenceLength
for (i in 0 until sliceCount) {
    val offset = 8 + i * storage.referenceLength
    val sliceRef = reference.copyOfRange(offset, offset + storage.referenceLength)
    storage.delete(sliceRef)
}
```

> **Note:** In practice, you do not need to manually delete each slice as shown above. Simply setting the field to `null` (e.g., `entity.file = null`) will make Encryptable to automatically delete all associated slices for you. The example above is provided for illustration and for cases where you need to perform deletion outside the normal entity lifecycle.

Using `@Sliced` allows Encryptable to transparently split large files into independently encrypted chunks, enabling you to store, retrieve, and process files of virtually any size—without hitting JVM memory limits. This approach also improves performance for large files by supporting parallel I/O and reducing memory pressure.

**Best practice:** Always use `@Sliced` for any binary field that might grow beyond a few hundred megabytes, or when you need to support streaming, parallel processing, or low-memory environments.

### How It Works Internally

Each slice is a **fully independent AES-256-GCM ciphertext** — its own IV, its own authentication tag — when the field is also annotated with `@Encrypt`. Without `@Encrypt`, slices are stored as raw bytes. All slices of the same field share the same secret as the entity — since they are parts of the same encrypted field, the same cryptographic isolation applies.

**The elegance of the implementation:** no new persistence mechanism is needed. The existing `ByteArray` reference field — already used to store a single storage reference — is simply extended to hold **N references, concatenated**:

```
// Non-sliced (current):
referenceBytes = ByteArray(storage.referenceLength)            // single reference → one file

// Sliced:
referenceBytes = ByteArray(8 + storage.referenceLength * N)   // 8-byte length header + N references
```

The first 8 bytes store the **original plaintext data length** as a big-endian `Long`. This is not
redundant — the slice count alone does not tell you the total length. All slices are exactly
`sizeMB` MB of plaintext, except the **last slice**, which can be anywhere from 1 byte to
`sizeMB` MB. Without the length header, you would have to fetch and decrypt the final slice
just to find out how large the output is — forcing a sequential dependency on the last slice
before any other work could begin.

Storing the length upfront means the total output size is known **before fetching a single slice**,
which enables two things:
- Pre-allocating a `ByteArray` of the exact output size, so each decrypted slice can be written
directly at its correct offset in parallel — no intermediate buffers, no reassembly step.
- Setting response headers (e.g. `Content-Length`) correctly from the very start of a download,
before any slice is fetched — which is required for the browser to show accurate download progress.

Each `IStorage` implementation defines its own reference size via the `referenceLength` property — there is no fixed size assumption. A GridFS implementation may use a 12-byte ObjectId reference, an S3 implementation may use a 16-byte CID, another backend may use a different size entirely. The slice count is always derived as:

```kotlin
val originalLength = java.nio.ByteBuffer.wrap(referenceBytes, 0, 8).long  // first 8 bytes → plaintext size
val sliceCount = (referenceBytes.size - 8) / storage.referenceLength
```

**No new fields, no new collections, no new persistence logic.** The existing reference mechanism handles it naturally — the `StorageHandler` detects `@Sliced` on the field and treats the reference as a list of N independent slice references instead of a single one.

This means:
- Each slice can be fetched, authenticated, and decrypted independently.
- Slices can be fetched and decrypted in parallel for maximum throughput.
- Memory-constrained environments process one slice at a time, never loading the full file into memory.
- Resumable uploads become architecturally natural — only failed slices need to be retried.
- **Update and delete work identically** to non-sliced fields — the `StorageHandler` deletes all N old slice references and creates N new ones atomically.

**Trade-off: memory vs. backend operations**

Slicing directly trades peak memory for a higher number of backend operations. A single non-sliced
field is always one read, one write, one delete — regardless of size. With `@Sliced`, every
operation is multiplied by the slice count (`fileSize / sliceKB`):

| File size | Slice size | Slices (N) | Write ops | Read ops | Delete ops |
|-----------|------------|------------|-----------|----------|------------|
| 16 MB     | 4 MB       | 4          | 4         | 4        | 4          |
| 64 MB     | 4 MB       | 16         | 16        | 16       | 16         |
| 256 MB    | 4 MB       | 64         | 64         | 64       | 64         |
| 1 GB      | 4 MB       | 256        | 256       | 256      | 256        |
| 2 GB      | 4 MB       | 512        | 512       | 512      | 512        |
| 2 GB      | 16 MB      | 128        | 128       | 128      | 128        |

On update, the cost doubles — N new slices are written before N old slices are deleted (atomic
replace). Choose a larger `sizeMB` to reduce operation count when memory headroom allows it.
Read operations can be parallelised to recover latency; write and delete are always sequential
to preserve atomicity.

**Storage backend cost implications**

Because `@Sliced` multiplies the operation count, the per-operation pricing of your storage
backend matters more than it would for a single-file field. The table below uses public list
prices (as of early 2026) for a **2 GB file with 4 MB slices (512 ops per read/write/delete)**:

| Provider | Storage (2 GB/mo) | Write (512 PUTs) | Read (512 GETs) | Egress (2 GB) | Notes |
|---|---|---|---|---|---|
| **Amazon S3 Standard** | ~$0.046 | ~$0.0002 | ~$0.0002 | ~$0.18 | Egress dominates at scale |
| **OCI Object Storage** | ~$0.051 | ~$0.00017 | ~$0.00017 | ~$0.00† | First 10 TB/mo egress free |
| **Cloudflare R2** | ~$0.030 | ~$0.0023 | ~$0.0002 | **$0.00** | No egress fees — best for high-read workloads |
| **Backblaze B2** | ~$0.012 | ~$0.0002 | ~$0.0002 | ~$0.02‡ | Free egress via Cloudflare (Bandwidth Alliance) |
| **Wasabi** | ~$0.014 | **$0.00** | **$0.00** | **$0.00** | No egress or per-op fees; minimum 90-day storage charge |
| **DigitalOcean Spaces** | ~$0.040 | **$0.00** | **$0.00** | ~$0.00§ | 1 TB/mo egress included; ops not billed separately |
| **Hetzner Object Storage** | ~$0.026 | **$0.00** | **$0.00** | ~$0.00§ | 1 TB/mo egress included; ops not billed separately |
| **Scaleway Object Storage** | ~$0.024 | **$0.00** | **$0.00** | ~$0.00¶ | 75 GB/mo egress free, then ~$0.01/GB; EU data residency |

> ⚠️ **Prices are approximate list prices and change over time. Always check the provider's current
> pricing page before making architecture decisions.**
>
> At 4 MB slices, per-operation costs are negligible — a full read or write cycle costs fractions
> of a cent in API fees. **Egress is the dominant cost for S3 at any meaningful scale.**
> R2, Wasabi, DigitalOcean Spaces, and Hetzner all eliminate or bundle egress, making them
> significantly cheaper than S3 for read-heavy workloads.
> Backblaze B2 achieves the same when paired with Cloudflare (Bandwidth Alliance).
> For European deployments with data residency requirements, Hetzner and Scaleway are the
> natural choices — no per-operation charges and predictable monthly billing.
> Wasabi's flat pricing (no ops, no egress) is the simplest cost model of all, with the caveat
> of a 90-day minimum storage charge per object.

### Why This Is Correct Cryptographically

This pattern mirrors how the **TLS record layer** works, each TLS record is independently encrypted with its own IV and auth tag, and sequence numbers are part of the authenticated data to prevent reordering. Applying this pattern at the storage layer is architecturally sound, well-understood, and battle-tested at internet scale.

**Ordering is guaranteed** by the position of each reference in the concatenated `ByteArray` — slice 0 starts at byte 8 (after the length header), slice 1 at byte `8 + referenceLength`, slice 2 at byte `8 + referenceLength * 2`, and so on. The order is deterministic and requires no separate metadata. Slice count is `(referenceBytes.size - 8) / storage.referenceLength`.

### Real-World Use Cases

`@Sliced` is valuable for any large binary field that needs to be processed in memory-constrained environments, fetched in parallel, or is simply too large to load entirely into memory at once.

**Any of these benefit from `@Sliced`:**
- 🎬 **Video and audio files** — large media stored on any S3-compatible backend, fetched and decrypted slice-by-slice. The storage backend never sees plaintext.
- 📄 **Large documents** — CAD files, medical imaging (DICOM), scientific datasets.
- 🖼️ **High-resolution images** — satellite imagery, medical scans, large RAW files.

> **Why streaming protocols (HLS, DASH, etc.) are out of scope for Encryptable:**\
> Encryptable slices a `ByteArray` **blindly** — it has no knowledge of what the bytes represent.
> Each slice is a fixed-size window into the raw encrypted data, nothing more. Streaming protocols
> like HLS and DASH are fundamentally different: each segment must be a **fully self-contained,
> format-valid archive** — a complete container unit with its own headers, codec metadata, timing
> information, and keyframes. A 4MB slice of an encrypted video file is not an HLS segment; it is
> an opaque chunk of ciphertext that happens to align with no media boundary whatsoever.
>
> To produce HLS/DASH segments, the video must first be transcoded and packaged into correctly
> bounded segments by a media processor — a concern entirely separate from encryption. Encryptable
> can then encrypt and store those pre-produced segments as individual `ByteArray` fields, but
> the segmentation itself is outside the framework's scope.
>
> How the stored slices or files are fetched, assembled, and delivered to end users — via download
> endpoints, edge functions, or any other mechanism — is the responsibility of the application.

**What this means in practice:**

- You can store and retrieve files larger than 2GB using Encryptable, as long as you use the `@Sliced` annotation to process them in chunks.
- You cannot read or write such files as a single `ByteArray` — this is a limitation of the Java platform, not Encryptable.
- All storage backends and the slicing mechanism are designed to support arbitrarily large files, but your application code must process them slice-by-slice.

---

## 📚 Learn More

- [INNOVATIONS.md](INNOVATIONS.md) — full technical analysis of the field-as-live-mirror pattern and comparison with existing solutions.
- [CONFIGURATION.md](CONFIGURATION.md) — configuring the storage threshold and other storage-related properties.
- Review `GridFSStorage`, `MemoryStorageImpl`, and `S3StorageImpl` in the source code for reference implementations.

---

**To our knowledge, no other framework in any language treats a `ByteArray` field as the source of truth for its storage backend — where assigning, updating, or nulling the field is the complete developer interaction, with zero API calls and zero lifecycle management. The individual techniques are well-established; the novelty is their composition into a single, seamless field-assignment experience.**
