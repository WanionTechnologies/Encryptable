# @HKDFId vs @Id: Understanding Entity Types in Encryptable

## Overview

Encryptable supports two distinct approaches to entity identification and encryption, each with different security characteristics and use cases. This document explains the fundamental differences between `@HKDFId` and `@Id` entities and when to use each approach.

## Quick Comparison

| Aspect | @HKDFId Entities | @Id Entities |
|--------|------------------|--------------|
| **Secret Source** | Entity's own secret (user-provided per request) | Master secret (from configuration) |
| **Cryptographic Isolation** | ✅ Complete isolation per entity | ❌ Shared master secret |
| **Key Derivation** | HKDF from entity secret | Direct use of master secret |
| **Master Secret Required** | ❌ No | ✅ Yes (for encryption) |
| **Secret Storage** | ❌ No (transient/request-scoped) | No (externally configured) |
| **Query Pattern** | Cryptographic addressing only | ✅ Traditional queries (findById, etc.) |
| **ID Type** | ByteArray (derived from secret) | String, ObjectId, UUID, etc. |
| **Security Model** | Strongest - per-entity keys | Shared key model |
| **Use Case** | User accounts, sensitive records | System data, shared resources |

## @HKDFId Entities: Maximum Cryptographic Isolation

### What They Are

Entities annotated with `@HKDFId` are **cryptographically isolated**. Each entity generates and stores its own unique secret, which is then used to derive encryption keys via HKDF (HMAC-based Key Derivation Function).

### How They Work

```kotlin
@Document
data class User(
    @HKDFId
    val id: ByteArray,
    
    @Encrypt
    val email: String,
    
    @Encrypt
    val phoneNumber: String
)
```

1. **Secret Generation**: Each entity gets a unique secret generated during creation
2. **Secret Provision**: The user provides this secret with each request (transient knowledge)
3. **Key Derivation**: All field encryption keys are derived from this secret using HKDF
4. **Complete Independence**: The entity's security is entirely independent of any master secret
5. **No Persistence**: Secrets are never stored - only held in memory during the request

### Security Characteristics

✅ **Cryptographically Isolated**: Compromise of one entity's secret does not affect others  
✅ **No Master Secret Dependency**: Even if a master secret exists, @HKDFId entities don't use it  
✅ **Per-Entity Key Space**: Each entity has its own unique cryptographic namespace  
✅ **Best Practice**: Recommended for user accounts and highly sensitive data  

### When to Use

- **User accounts** with sensitive personal information
- **Multi-tenant systems** where data isolation is critical
- **High-security scenarios** requiring per-record encryption
- **Regulatory compliance** (GDPR, HIPAA) requiring data segregation

## @Id Entities: Shared Master Secret Encryption

### What They Are

Entities annotated with standard `@Id` can now support encryption using a **shared master secret**. This is simpler but provides less cryptographic isolation than @HKDFId.

### How They Work

```kotlin
@Document
data class SystemConfig(
    @Id
    val id: String,
    
    @Encrypt
    val apiKey: String,
    
    @Encrypt
    val webhookUrl: String
)
```

1. **Master Secret Required**: A master secret must be configured in your application
2. **Shared Encryption**: All @Id entities share the same master secret for encryption
3. **No Per-Entity Secret**: These entities don't store their own secrets
4. **Simpler Model**: Easier to manage but with shared security boundaries

### Master Secret Configuration

The master secret must be configured via one of these methods:

#### 1. Environment Variable
```bash
export ENCRYPTABLE_MASTER_SECRET="your-secure-master-secret-here"
```

#### 2. Application Properties
```properties
encryptable.master.secret=your-secure-master-secret-here
```

#### 3. Programmatic Configuration
```kotlin
MasterSecretHolder.setMasterSecret("your-secure-master-secret-here")
```

### Security Characteristics

⚠️ **Shared Security Boundary**: All @Id entities share the same master secret  
⚠️ **Single Point of Compromise**: If the master secret leaks, all @Id entity data is exposed  
✅ **Simpler Key Management**: Only one secret to manage  
✅ **Lower Overhead**: No per-entity secret storage  

### When to Use

- **System-level configuration** that isn't user-specific
- **Shared resources** like API keys, webhook URLs
- **Traditional database queries** where you need to query by readable IDs (String, ObjectId, etc.)
- **Existing applications** that rely on standard database query patterns
- **Low-security scenarios** where convenience outweighs isolation
- **Backwards compatibility** for existing systems using standard @Id

## Historical Context

### Previous Limitation (< v1.0.4)

Prior to version 1.0.4, entities with `@Id` annotation **could not use `@Encrypt`** fields at all. The framework only supported encryption for `@HKDFId` entities.

This limitation existed because:
- There was no master secret mechanism
- The framework was designed exclusively around per-entity secrets
- @Id entities had no way to derive encryption keys

### Current Support (>= v1.0.4)

Starting with version 1.0.4, the **master secret feature** was introduced, enabling:
- ✅ `@Encrypt` fields on @Id entities
- ✅ Flexible configuration options (environment, properties, programmatic)
- ✅ Backwards compatibility (master secret is optional if you only use @HKDFId)

## Query Pattern Implications

### Traditional Queries vs Cryptographic Addressing

One of the most significant practical differences between @Id and @HKDFId entities is **how you query them**:

#### @Id Entities: Traditional Database Queries ✅

With @Id entities, you can use **standard database query patterns**:

```kotlin
// Query by readable ID
val config = repository.findById("payment-gateway-config")

// Query by indexed fields
val configs = repository.findByName("production")

// Complex queries work normally
val results = repository.findByCreatedDateAfter(yesterday)
```

**Benefits**:
- ✅ Works with existing query patterns
- ✅ No cryptographic addressing required
- ✅ Standard Spring Data repository methods work out of the box
- ✅ Can query without knowing the secret
- ✅ Suitable for system-level entities that need to be found by administrators

#### @HKDFId Entities: Cryptographic Addressing Required 🔐

With @HKDFId entities, the ID itself is derived from the secret (**cryptographic addressing**):

```kotlin
// Must use cryptographic addressing - can't query by arbitrary fields
val user = userService.findBySecret(userSecret)  // ID is derived from secret

// Traditional queries by other fields are NOT supported
// This WON'T work: repository.findByEmail("user@example.com")
```

**Characteristics**:
- 🔐 ID is cryptographically derived from the entity's secret
- 🔐 Cannot query by email, username, or other fields
- 🔐 Must know the secret to access the entity
- 🔐 Maximum privacy - no way to enumerate or search entities

### Choosing Based on Query Requirements

**Use @Id if you need**:
- Traditional database queries (findById, findByField, etc.)
- Admin access without user secrets
- Search and enumeration capabilities
- Integration with existing query-based systems

**Use @HKDFId if you need**:
- Cryptographic addressing (secret-based access only)
- Maximum privacy (no enumeration possible)
- Per-entity cryptographic isolation
- Zero ability to query without the secret

## Security Implications

### Cryptographic Isolation

The key security difference is **cryptographic isolation**:

#### @HKDFId: Complete Isolation
```
User A Secret → HKDF → User A Field Keys
User B Secret → HKDF → User B Field Keys
```
If User A's secret is compromised, User B's data remains secure.

#### @Id: Shared Security
```
Master Secret → Config A Field Keys
Master Secret → Config B Field Keys
```
If the master secret is compromised, all @Id entity data is exposed.

### Reference Storage Protection

When @Id entities reference @HKDFId entities, Encryptable stores only the **ID** (not the secret) to maintain cryptographic isolation. This ensures that even if the master secret leaks, @HKDFId entity secrets remain protected.

```kotlin
@Document
data class SystemConfig(
    @Id
    val id: String,
    
    // Stores only the User's ID, not their secret
    val primaryUser: User
)
```

#### Automatic Reference Format Migration (v1.0.4+)

For @Id entities that reference @HKDFId entities, v1.0.4+ automatically migrates the **reference storage format** (not the data encryption itself):

- **What's Migrated**: Secret-based references → ID-only references
- **When**: During entity loading, if secret-based references are detected
- **On Save**: The corrected version (ID-only) is persisted back to the database
- **Security Benefit**: Prevents secret leakage through @Id entity compromise
- **Scope**: Only affects how references are stored, not field encryption

**Important**: This is **not** a data encryption migration. Encryptable remains stateless - it cannot automatically encrypt previously unencrypted data. This migration only changes the format of entity references to maintain cryptographic isolation.

## Best Practices

### ✅ Do's

1. **Use @HKDFId for user data**: Maximum security for personal information
2. **Use @Id for system data**: Appropriate for shared configuration
3. **Protect the master secret**: Store it securely (environment variables, secret managers)
4. **Separate concerns**: Don't mix security models within the same domain
5. **Document your choice**: Make it clear why each entity type was chosen

### ❌ Don'ts

1. **Don't use @Id for multi-tenant user data**: Risk of cross-tenant exposure
2. **Don't hardcode the master secret**: Always externalize configuration
3. **Don't assume equivalence**: @HKDFId and @Id have fundamentally different security properties
4. **Don't skip rotation planning**: Have a strategy for master secret rotation
5. **Don't over-rely on master secret**: Use @HKDFId when isolation matters

---

## Migration Considerations

> **Note**: The following sections describe migration strategies for various scenarios. Encryptable is stateless and cannot automatically transform existing data - all migrations require manual implementation.

### From Unencrypted to Encrypted Fields

When adding `@Encrypt` to previously unencrypted fields, Encryptable is stateless and cannot automatically encrypt existing data.

**Recommended Approach: Create New Entity + Lazy Migration**

This applies to both @Id and @HKDFId entities:

1. **Create a new entity class** with `@Encrypt` annotations
2. **Configure master secret** (for @Id entities only)
3. **Implement lazy migration**:
   ```kotlin
   // Try to load from new encrypted entity first
   val entity = newRepository.findById(id) 
       ?: oldRepository.findById(id)?.let { old ->
           // Migrate on first access
           val new = old.toEncryptedEntity()
           newRepository.save(new)
           oldRepository.delete(old)
           new
       }
   ```
4. **Gradual migration**: Entities are migrated as they're accessed
5. **Clean up**: Remove old entities after sufficient time

**Benefits**:
- ✅ No downtime required
- ✅ Gradual, risk-free migration
- ✅ Easy rollback (keep old entities until confident)
- ✅ Works identically for both @Id and @HKDFId entities

**Alternative: Batch Migration**

If you prefer upfront migration:
1. Create a migration script that loads all old entities
2. Convert each to the new encrypted format
3. Save to the new collection/schema
4. Verify data integrity before switching over

⚠️ **Important**: Encryptable cannot automatically encrypt previously unencrypted fields. You must handle migration manually using one of these approaches.

### From Encrypted to Unencrypted Fields

The same principle applies when **removing** `@Encrypt` from fields - Encryptable is stateless and cannot automatically decrypt and migrate existing data.

**Recommended Approach: Create New Entity + Lazy Migration**

This applies to both @Id and @HKDFId entities:

1. **Create a new entity class** without `@Encrypt` annotations on the target fields
2. **Implement lazy migration**:
   ```kotlin
   // Try to load from new unencrypted entity first
   val entity = newRepository.findById(id) 
       ?: oldRepository.findById(id)?.let { old ->
           // Migrate on first access (decrypt during read)
           val new = old.toUnencryptedEntity()
           newRepository.save(new)
           oldRepository.delete(old)
           new
       }
   ```
3. **Gradual migration**: Entities are migrated as they're accessed
4. **Clean up**: Remove old encrypted entities after sufficient time

**Benefits**:
- ✅ No downtime required
- ✅ Gradual, risk-free migration
- ✅ Easy rollback (keep old entities until confident)
- ✅ Works identically for both @Id and @HKDFId entities

⚠️ **Important**: Encryptable cannot automatically decrypt and remove encryption from fields. You must handle migration manually using this approach.

### Upgrading from < v1.0.4 to v1.0.4+

#### If You Only Use @HKDFId Entities
✅ **No action required** - Simply upgrade, everything continues to work

#### If You Have @Id Entities That Reference @HKDFId Entities
✅ **Automatic Reference Format Migration**: The framework automatically converts secret-based references to ID-only references during entity loading  
✅ **No Manual Work**: Happens transparently as entities are loaded and saved  
✅ **Scope**: Only affects reference storage format, not field encryption

**Action Required**: Simply upgrade to v1.0.4+ - the reference format migration is automatic.

#### If You Want to Add Encryption to Existing Entities
⚠️ **Manual Migration Required**: See "From Unencrypted to Encrypted Fields" above (applies to both @Id and @HKDFId entities)

### From @Id to @HKDFId

This is a **breaking change** that requires:
1. Schema changes (ID type changes from String/ObjectId to ByteArray)
2. Complete data migration
3. Secret generation for all existing entities
4. Careful planning and testing

**Recommendation**: Design your entity types correctly from the start.

---

## Example: Choosing the Right Approach

### Scenario 1: E-Commerce Platform

```kotlin
// Use @HKDFId: User accounts need maximum isolation
@Document
data class Customer(
    @HKDFId
    val id: ByteArray,
    @Encrypt val email: String,
    @Encrypt val paymentInfo: String
)

// Use @Id: System config is shared anyway
@Document
data class PaymentGatewayConfig(
    @Id
    val id: String,
    @Encrypt val apiKey: String
)
```

### Scenario 2: Healthcare System

```kotlin
// Use @HKDFId: Patient records require HIPAA-level isolation
@Document
data class Patient(
    @HKDFId
    val id: ByteArray,
    @Encrypt val ssn: String,
    @Encrypt val diagnosis: String
)

// Use @HKDFId: Even doctors need individual isolation
@Document
data class Doctor(
    @HKDFId
    val id: ByteArray,
    @Encrypt val licenseNumber: String
)

// Use @Id: System settings are administrative
@Document
data class HospitalConfig(
    @Id
    val id: String,
    @Encrypt val hl7Endpoint: String
)
```

## Conclusion

The choice between `@HKDFId` and `@Id` is a **fundamental security decision**:

- **@HKDFId** provides maximum security through cryptographic isolation - each entity is independent
- **@Id** provides convenience through shared master secret - simpler but less isolated

**Version 1.0.4+ enables traditional query patterns**: With @Id entities now supporting encryption, you can use Encryptable without being forced into cryptographic addressing. This makes the framework accessible to applications that need standard database queries while still benefiting from encryption at rest.

Choose @HKDFId when security and isolation are paramount.  
Choose @Id when convenience and shared management are acceptable.

**Remember**: The master secret is required for @Id entities to use encryption. Without it, @Encrypt fields on @Id entities will fail.

---

**See Also**:
- [Configuration Guide](CONFIGURATION.md)
- [Secret Rotation](SECRET_ROTATION.md)
- [Security Best Practices](BEST_PRACTICES.md)
- [Not Zero Knowledge](NOT_ZERO_KNOWLEDGE.md)

