# ORM-Like Features in Encryptable

Encryptable brings advanced **ORM-like (Object-Relational Mapping) features** to MongoDB, enabling developers to model complex relationships and data integrity constraints with the simplicity and power typically found in relational database frameworks. This document details these features, including relationship modeling, the `@PartOf` annotation, cascade delete, and best practices for using these capabilities securely and efficiently.

---

## 🔗 Relationship Modeling

Encryptable supports natural object composition for modeling relationships:

- **One-to-One:** Include another entity as a field in your entity class.
- **One-to-Many:** Use a list of entities as a field in your entity class.
- **Many-to-Many:** Entities can reference multiple other entities, forming bidirectional or unidirectional associations.

No special annotations are required for these relationships—just use standard Kotlin/Java object fields and collections.

### **Example: One-to-Many**
```kotlin
class User : Encryptable<User>() {
    @HKDFId
    override var id: CID? = null
    var name: String? = null
    var posts: List<Post> = listOf()
}

class Post : Encryptable<Post>() {
    @HKDFId
    override var id: CID? = null
    var content: String? = null
}
```

---

## 🔄 Many-to-Many Relationships

Many-to-many relationships are modeled by having each entity reference a collection of the other. Encryptable manages these references naturally, but **be aware**: if you rotate the secret of an entity in a many-to-many relationship, the encrypted references will break, and the relationship will be lost unless all related entities are updated accordingly.

### **Example: Many-to-Many**
```kotlin
class Student : Encryptable<Student>() {
    @HKDFId
    override var id: CID? = null
    var name: String? = null
    var courses: List<Course> = listOf()
}

class Course : Encryptable<Course>() {
    @HKDFId
    override var id: CID? = null
    var title: String? = null
    var students: List<Student> = listOf()
}
```

---

## 🧩 The `@PartOf` Annotation and Cascade Delete

The `@PartOf` annotation is used to mark a child entity as being part of a parent entity. This enables **cascade delete**: when the parent is deleted, all children marked with `@PartOf` are automatically deleted as well, ensuring referential integrity.

### **Example: Cascade Delete**
```kotlin
class Invoice : Encryptable<Invoice>() {
    @HKDFId
    override var id: CID? = null
    @PartOf
    var items: List<InvoiceItem> = listOf()
}

class InvoiceItem : Encryptable<InvoiceItem>() {
    @HKDFId
    override var id: CID? = null
    var description: String? = null
    var amount: Double? = null
}
```
- Deleting an `Invoice` will automatically delete all associated `InvoiceItem` entities.

---

## 🎭 Polymorphic Nested Entities

**Industry-First Feature:** Encryptable supports **100% transparent polymorphism** for nested `Encryptable<*>` fields—no annotations, no discriminators, no configuration required.

### **How It Works**

When you declare a field as an abstract class or interface type (that extends `Encryptable`), you can set any concrete implementation to that field. The framework automatically tracks and preserves the concrete type across save/load cycles.

### **Example: Polymorphic Payment**
```kotlin
// Abstract base class
abstract class Payment<P : Payment<P>> : Encryptable<P>() {
    abstract override var id: CID?
    @Encrypt var amount: Double? = null
}

class CreditCardPayment : Payment<CreditCardPayment>() {
    @HKDFId
    override var id: CID? = null
    @Encrypt var cardNumber: String? = null
}

class PixPayment : Payment<PixPayment>() {
    @HKDFId
    override var id: CID? = null
    @Encrypt var pixKey: String? = null
}

// Order with polymorphic field
class Order : Encryptable<Order>() {
    @HKDFId
    override var id: CID? = null
    @PartOf var payment: Payment<*>? = null  // 🎯 Polymorphic field!
}

// Usage - completely transparent
val order = Order().withSecret(secret).apply {
    payment = CreditCardPayment().withSecret(generateSecret()).apply {
        cardNumber = "4532..."
        amount = 100.0
    }
}
orderRepository.save(order)

// Concrete type preserved after save/load
val retrieved = orderRepository.findBySecretOrNull(secret)!!
retrieved.payment is CreditCardPayment  // ✅ true!
```

### **Key Benefits**

- ✅ **Zero configuration** - No `@Type`, `@JsonTypeInfo`, or discriminator annotations needed
- ✅ **Type preservation** - Concrete type automatically survives save/load cycles
- ✅ **Runtime flexibility** - Change payment type at runtime, old one auto-deleted via `@PartOf`
- ✅ **Storage optimized** - Type stored only when it differs from field declaration
- ✅ **Works with encryption** - All polymorphic fields can use `@Encrypt`

> **⚠️ Important:** Polymorphism applies **only to single `Encryptable<*>` fields**. It does **not** apply to `List<Encryptable<*>>` fields—list elements must use concrete types at declaration.

---

## 🔍 Automatic Change Detection & Efficient Updates

**Zero-Overhead Feature:** Encryptable automatically detects which fields have changed and updates **only those fields** in MongoDB—no manual dirty tracking, no explicit update calls required.

### **How It Works**

The framework computes hash codes for all persisted fields when an entity is loaded. At the end of the request (via `flushThenClear()`), it compares the current field values with the initial hash codes to identify changes. Only modified fields are sent to MongoDB, dramatically reducing write amplification.

### **Example: Partial Field Update**
```kotlin
// Load entity with many fields
val user = userRepository.findBySecretOrNull(secret)!!
user.email = "newemail@example.com"  // Change only one field

// At request end, framework automatically:
// 1. Detects only 'email' changed (via hashCode comparison)
// 2. Re-encrypts all @Encrypt fields (optimization opportunity for future)
// 3. Issues MongoDB update: { $set: { email: "..." } }  ← Only changed field sent!
// 4. Other 99 fields NOT transmitted to MongoDB - 99% bandwidth reduction!
```

### **Key Benefits**

- ✅ **Automatic detection** - No manual `markDirty()` or `update()` calls needed
- ✅ **Write optimization** - Reduces database write load and bandwidth
- ✅ **Better concurrency** - Smaller updates reduce lock contention
- ✅ **Works with encryption** - Efficiently handles encrypted field updates
- ✅ **Binary field optimization** - Smart hashing for large ByteArray fields (first 4KB checksum)

### **Performance Impact**

```kotlin
// Large document (100 fields, 100KB total)
val document = repository.findBySecretOrNull(secret)!!
document.oneField = "updated"

// ❌ Traditional ORM: Writes entire 100KB document
// ✅ Encryptable: Writes only changed field (~1KB)
// Result: 99% reduction in write bandwidth
```

### **Technical Details**

- **Hash computation:** Uses Kotlin's `hashCode()` for most types, special handling for `ByteArray` (first 4KB)
- **Parallel processing:** Field hashes computed in parallel for performance
- **Bulk updates:** Batches multiple entity updates into single MongoDB bulk operation
- **Low collision rate:** Hash-based detection has very low false positive rate
- **Bandwidth optimization:** Only changed fields are transmitted to MongoDB (currently, all `@Encrypt` fields are re-encrypted locally, but this doesn't affect network bandwidth—future optimization will make re-encryption selective too)

### **Audit Pattern with `touch()`**

For audit fields that should update on every access (e.g., `lastAccessTime`), override the `touch()` method:

```kotlin
class AuditEntity : Encryptable<AuditEntity>() {
    @HKDFId
    override var id: CID? = null
    
    var lastAccessTime: Long? = null
    
    override fun touch() {
        lastAccessTime = System.currentTimeMillis()
    }
}

// Usage - touch() called automatically after load
val entity = repository.findBySecretOrNull(secret)!!
// lastAccessTime already updated, will be persisted at request end
```

---

## ⚠️ Best Practices and Warnings

- **Secret Rotation:** Rotating the secret of a child entity (with `@PartOf`) or an entity in a many-to-many relationship will break the relationship, as references are encrypted. **Avoid rotating secrets for these entities unless you are prepared to manually update all related references.**
- **Cascade Delete:** Use `@PartOf` only for true parent-child relationships where cascade delete is desired. For shared or referenced entities, do not use `@PartOf`.
- **Cascade Delete in Many-to-Many:** ⚠️ **Be extremely careful:** If you use `@PartOf` in a many-to-many relationship, deleting one entity can trigger a cascade that deletes all related entities, which in turn may delete more entities, potentially causing a StackOverflowError or deleting your entire dataset. **There is no built-in check to prevent this.**
- **Lazy Loading:** Encryptable supports lazy loading for large or binary fields, optimizing performance for large datasets.

---

## 📊 Feature Comparison Table

| Feature                | MongoDB Native | JPA/Hibernate | Encryptable Framework |
|------------------------|----------------|---------------|----------------------|
| One-to-One             | Manual refs    | Yes           | ✅ Yes (object field) |
| One-to-Many            | Manual refs    | Yes           | ✅ Yes (list field)   |
| Many-to-Many           | Manual refs    | Yes           | ✅ Yes (links/refs)   |
| Cascade Delete         | Manual         | Yes           | ✅ Yes (`@PartOf`)    |
| Polymorphic Entities   | Manual         | ⚠️ Needs `@Inheritance` | ✅ Yes (transparent)  |
| Change Detection       | Manual         | ✅ Yes (dirty tracking) | ✅ Yes (automatic)   |
| Partial Field Updates  | Manual         | ✅ Yes        | ✅ Yes (hash-based)  |
| Lazy Loading           | No             | Yes           | ✅ Yes               |
| Annotation-Based       | No             | Yes           | ⚠️ Only @PartOf      |

---

## 📚 Further Reading
- [Secret Rotation Considerations](SECRET_ROTATION.md)
- [Innovations Overview](INNOVATIONS.md)