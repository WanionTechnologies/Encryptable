# 🗑️ Deleting Entities Without Secrets: Handling Temporary & Expiring Data

**A Guide to Removing Expired Entities in Encryptable Without Knowing Their Secrets**

---

## Introduction

Encryptable is designed to maximize privacy and security by requiring secrets for most entity operations, including deletion. However, there are scenarios—such as temporary or expiring entities—where you may need to delete records without knowing their secrets. This document explains how to safely and efficiently handle such cases, even though it is not an out-of-the-box feature.

---

## The Challenge: Deleting Without Secrets

- **Default Behavior:** Encryptable entities are typically deleted using their secret, ensuring that only authorized operations occur.
- **Problem:** For temporary entities (e.g., tokens, sessions, or records with an expiration date), you may want to delete them based on criteria like lastAccess or createdAt, without having the secret.
- **Note:** When deleting by ID or other non-secret criteria, the retrieved entity cannot be decrypted or accessed in its original form, as the secret is required for decryption.

---

## Practical Approach: Expiration Logic

You can implement custom expiration logic by querying entities on non-secret fields (such as lastAccess or createdAt) and deleting those that have expired.

**Example Scenario:**
- Entities should be deleted if lastAccess is more than 7 days ago.

**Sample Implementation (Batch Deletion):**
```kotlin
val expirationThreshold = Instant.now().minus(Duration.ofDays(7))
val expiredEntities = repository.findAllByLastAccessBefore(expirationThreshold)
val expiredIds = expiredEntities.mapNotNull { it.id }
repository.deleteAllById(expiredIds) // Batch delete by IDs
```
- This approach uses a custom repository query to find expired entities and deletes them in a single batch operation using their IDs.
- You can also use scheduled jobs or background tasks to automate this cleanup process.

---

## Indexing for Performance

To improve the performance of your expiration queries, it's crucial to index the fields used in the deletion criteria, like `lastAccess`. Indexing allows the database to quickly locate and access the records that need to be deleted, significantly speeding up the process.

**Indexing Example:**
To ensure efficient lookups and deletions, annotate your expiration field (such as `lastAccess`) with `@Indexed`:

```kotlin
import org.springframework.data.mongodb.core.index.Indexed

@Indexed
var lastAccess: Instant? = null
```
This creates a database index on `lastAccess`, enabling O(log n) queries for expiration logic.

---

## Limitations & Considerations

- **Not Out-of-the-Box:** Encryptable now provides a way to delete entities without secrets using `deleteById`, specifically for temporary or non-sensitive entities. You must still implement custom repository queries to identify which entities should be deleted.
- **Security:** Ensure that only non-sensitive, temporary, or expiring entities are deleted this way. For sensitive data, always prefer secret-based deletion.
- **Privacy:** Deleting by ID or other non-secret fields should be limited to cases where privacy is not compromised.
- **Performance:** Batch deletion and scheduled cleanup jobs can help manage large numbers of temporary entities efficiently.

---

## Best Practices

- Use expiration logic only for temporary data.
- Document and audit all custom deletion logic for compliance and maintainability.
- Schedule regular cleanup jobs to avoid data bloat and ensure timely removal of expired records.
- **Index the field used to track `lastAccess` (or similar expiration criteria) in your database.** This ensures efficient lookups and deletions, reducing query complexity from O(n) to O(log n) for large datasets.

---

## Summary

While Encryptable prioritizes secret-based operations for privacy and security, it is possible to delete temporary or expiring entities without knowing their secrets by leveraging custom queries and repository methods. This approach is flexible and practical for managing tokens, sessions, and other short-lived data.

---

*Last updated: 2025-11-14*
