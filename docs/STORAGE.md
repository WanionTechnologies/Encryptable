# Encryptable: Storage Abstraction & Extensibility

Encryptable introduces a flexible and extensible storage abstraction layer, enabling seamless integration with various storage backends for large binary data. This document explains the motivation, design, and extensibility of the Storage system in Encryptable.

## 🚀 Why Storage Abstraction Exists

Modern applications often need to store large binary data (such as files, images, or documents) alongside structured entity data. Storing such data directly in the database can be inefficient or impractical, especially as data grows. Encryptable's storage abstraction solves this by:

- **Decoupling storage logic from encryption and entity management.**
- **Supporting multiple storage backends** (e.g., MongoDB, S3, file system, or custom solutions).
- **Enabling lazy loading and efficient cleanup of large binary fields.**
- **Allowing users to extend or replace storage mechanisms without modifying core framework logic.**

## 📦 Out-of-the-Box Storage: GridFSStorage

Encryptable provides a ready-to-use storage backend based on MongoDB GridFS, called `GridFSStorage`.

- **What is GridFSStorage?**
  - GridFS is MongoDB's specification for storing and retrieving large files, splitting them into smaller chunks for efficient storage and retrieval.
  - `GridFSStorage` integrates seamlessly with Encryptable, allowing you to store large binary data (such as files, images, or documents) without worrying about database size limits or performance issues.
- **Benefits:**
  - No additional setup required—works out of the box with MongoDB.
  - Supports lazy loading, efficient cleanup, and automatic management of large binary fields.
  - Ensures binary data is stored securely and efficiently alongside your encrypted entities.
- **When to use:**
  - Ideal for applications already using MongoDB and needing to store large files or binary data.
  - Use as the default storage backend, or as a reference implementation for building your own custom storage solutions.

You can always implement and register your own storage backend (such as Amazon S3, S3-compatible services like MinIO or Wasabi, or file system) if your application has different requirements.

## 🧑‍💻 Reference Implementations for Custom Storage

In addition to being the default storage backend, `GridFSStorage` serves as a practical template for implementing your own custom `IStorage` backends. Its code demonstrates:

- Minimal boilerplate and clear separation of concerns
- How to implement all required `IStorage` methods
- Proper use of Spring bean registration
- Integration with Encryptable's storage abstraction

**MemoryStorageImpl** is another example implementation, designed for testing and demonstration purposes. It provides an in-memory storage backend with minimal code, making it ideal for unit tests, development, or as a starting point for simple custom storage solutions.

**Recommendation:**
If you are building a new storage backend (such as S3, file system, or a cloud provider), review the `GridFSStorage` and `MemoryStorageImpl` implementations in the source code as starting points. You can adapt their structure and patterns for your own needs, ensuring a smooth and idiomatic integration with Encryptable.

## 🧩 How It Works

- **Storage is abstracted via the `IStorage` interface.**
- **Entities can declare fields for large binary data, which are automatically stored using the configured storage backend.**
- **The framework provides built-in storage implementations (e.g., for MongoDB), but users can supply their own.**
- **Storage operations (create, read, delete) are handled transparently, with encryption/decryption managed separately.**

## 🛠️ Creating Custom Storage Backends

Encryptable is designed for extensibility. You can implement your own storage backend (for example, Amazon S3, S3-compatible services, file system, or a cloud provider) by following these steps:

1. **Implement the `IStorage` interface**
   - Define how to store, retrieve, and delete binary data.
   - Annotate your implementation with `@Component` (or another Spring stereotype annotation) so Encryptable can retrieve it as a Spring bean.
   - Ensure your implementation is thread-safe and efficient for your use case.
   - Example:
     ```kotlin
     import org.springframework.stereotype.Component
     
     @Component
     class MyS3Storage : IStorage<MyS3Reference> {
         // ...implementation...
     }
     ```
2. **Create a custom annotation for your storage**
   - Define an annotation (e.g., `@S3Storage`) and annotate it with `@Storage(storageClass = MyS3Storage::class)`, where `MyS3Storage` is your implementation of `IStorage`.
   - Example:
     ```kotlin
     @Target(AnnotationTarget.FIELD)
     @Retention(AnnotationRetention.RUNTIME)
     @Storage(storageClass = MyS3Storage::class)
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

## 📎 Storage Reference Details

The storage reference object (the type parameter of `IStorage<ReferenceObj>`) is designed to be a compact, database-stored reference for retrieving the actual binary data and any associated metadata needed for file access.

- **Reference Size:**
  - The reference can be up to 16 bytes, making it efficient to store directly in your entity documents.
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

If the field is also annotated with a storage annotation (e.g., `@S3Storage`), Encryptable will automatically handle encryption and decryption for you. **Your storage implementation does not need to perform any encryption or decryption itself**—Encryptable ensures that all data is securely encrypted before being passed to storage, and decrypted when read back.

This separation of concerns keeps your storage code simple and focused only on storing and retrieving raw bytes, while Encryptable transparently manages all cryptographic operations.

## ⚠️ Handling Failures and Robustness

When implementing a custom storage backend, it is essential to prepare for potential failures such as network timeouts, partial writes, or storage unavailability. Your `IStorage` implementation should:

- Handle and propagate exceptions appropriately (e.g., IO/network errors, timeouts, permission issues).
- Ensure atomicity where possible (avoid partial writes or orphaned files on failure).
- Implement retries or fallback logic if suitable for your storage backend.
- Clean up any temporary or incomplete data in case of errors.
- Log errors and provide meaningful feedback for troubleshooting.

Encryptable will call your storage methods as part of entity persistence. If a storage operation fails, the exception will propagate up to the application, so robust error handling is critical for data integrity and user experience.

## 🌐 Example Use Cases

- **Amazon S3 or S3-Compatible Storage:** Store large files in Amazon S3, MinIO, Wasabi, or any S3-compatible service, with metadata for bucket and object key.
- **File System Storage:** Store files on disk, referencing them by path or unique identifier.
- **Hybrid Storage:** Use different storage backends for different entity types or fields.

## 📚 Learn More

- See the framework documentation for details on configuring and using storage backends.
- Review the `IStorage` interface and built-in implementations for reference.
- For advanced scenarios, consult the source code and examples.

---

**Storage abstraction empowers Encryptable users to scale, optimize, and customize binary data handling for any application scenario.**
