# ORM-Like Features in Encryptable

## 🏗️ Overview

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
| Lazy Loading           | No             | Yes           | ✅ Yes               |
| Annotation-Based       | No             | Yes           | ⚠️ Only @PartOf      |

---

## 📚 Further Reading
- [Secret Rotation Considerations](SECRET_ROTATION.md)
- [Innovations Overview](INNOVATIONS.md)