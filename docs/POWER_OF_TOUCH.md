# 🖐️ The Power of `touch`: From Simple to Advanced Usages

**A Practical Guide to Leveraging the `touch` Method in Encryptable Entities**

---

## Introduction

The `touch` method in Encryptable entities is a flexible lifecycle hook designed to track, audit, and respond to access events. Whether you need basic timestamping or advanced security automation, `touch` can be customized to fit your application's needs.

**When is `touch` called?**
- The `touch` method is automatically called on every entity loaded via `findBySecret` (after integrity checks).
- This ensures that every access to an entity is tracked, audited, and can trigger custom logic, making it a central point for monitoring and automation.

---

## 1. Simple Usage: Timestamping Access

The most common use of `touch` is to update a field (such as `lastAccess`) whenever an entity is accessed or loaded. This provides a basic audit trail and helps track user activity.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
}
```

- **Benefit:** Easy way to know when an entity was last used.
- **Use Case:** User profile access, document viewing, session management.

---

## 2. Intermediate Usage: Audit Logging

Extend `touch` to log access events for compliance, monitoring, or forensic analysis. This can include writing to a database, sending logs to a SIEM, or appending to an audit file.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    auditLogger.logAccess(userId, entityId, lastAccess)
}
```

- **Benefit:** Maintains a detailed record of who accessed what and when.
- **Use Case:** Financial records, healthcare data, regulated environments.

---

## 3. Advanced Usage: Security Automation & Notifications

The `touch` method can trigger automated security actions, such as sending notifications, alerts, or initiating multi-factor authentication. Integrate with messaging systems, security platforms, or custom workflows.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    if (isSensitiveAccess) {
        notificationService.sendSecurityAlert(userId, entityId)
        securityWorkflow.initiateMFA(userId)
    }
}
```

- **Benefit:** Real-time response to sensitive or suspicious access events.
- **Use Case:** Account logins, privileged data access, incident response.

---

## 4. Expert Usage: Custom Business Logic & Integrations

`touch` can be the entry point for complex business logic, such as updating related entities, triggering external API calls, or integrating with third-party compliance tools.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    externalAuditApi.reportAccess(userId, entityId, lastAccess)
    relatedEntity.updateStatus("accessed")
}
```

- **Benefit:** Seamless integration with enterprise systems and business processes.
- **Use Case:** Cross-system audits, compliance automation, workflow orchestration.

---

## 5. Self-Destruct Usage: Automated Entity Deletion

The `touch` method can be used to make an entity self-destruct by calling its repository and deleting itself by secret. This is useful for implementing ephemeral or time-limited entities, such as one-time tokens or temporary access grants.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    if (shouldSelfDestruct)
        metadata.repository.deleteBySecret(secret)
}
```

- **Benefit:** Enables automatic cleanup of sensitive or temporary data.
- **Use Case:** One-time tokens, temporary access entities, audit-triggered deletion.

---

## 6. Access Rate Limiting

Use `touch` to enforce rate limits on entity access, preventing abuse or brute-force attacks.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    accessCounter++
    if (accessCounter > MAX_ACCESS_PER_HOUR) {
        metadata.repository.deleteBySecret(secret) // Optionally self-destruct or lock
        notificationService.sendRateLimitAlert(userId, entityId)
    }
}
```

- **Benefit:** Prevents excessive or abusive access to sensitive entities.
- **Use Case:** API keys, sensitive resources, anti-abuse controls.

---

## 7. Dynamic Permission Update

Use `touch` to update permissions or roles dynamically based on access patterns or business rules.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    if (shouldElevatePermissions()) {
        permissions.add("TEMP_ADMIN")
        notificationService.sendPermissionChange(userId, entityId)
    }
}
```

- **Benefit:** Enables adaptive security and business logic.
- **Use Case:** Temporary privilege escalation, workflow automation.

---

## 8. Triggering External Workflows

Use `touch` to initiate external business processes, such as starting a background job or workflow in another system.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    workflowService.startBackgroundJob(entityId)
}
```

- **Benefit:** Integrates entity access with broader business operations.
- **Use Case:** Document processing, asynchronous tasks, workflow triggers.

---

## 9. Geo-Location Audit

Use `touch` to record the location of access for geo-fencing or compliance.

**Example:**
```kotlin
override fun touch() {
    lastAccess = Instant.now()
    lastAccessLocation = geoService.getCurrentLocation()
    auditLogger.logGeoAccess(userId, entityId, lastAccessLocation)
}
```

- **Benefit:** Adds location-based security and audit capabilities.
- **Use Case:** Regulatory compliance, fraud detection, geo-fencing.

---

## Important:
- Any changes made by `touch` (such as updating fields or permissions) will be automatically persisted via an UPDATE operation at the end of the request lifecycle, **unless the field is annotated with `@Transient`**. This ensures that all non-transient modifications triggered by `touch` are reliably saved without requiring manual intervention.

## Best Practices

- You do not need to call `super.touch()` when overriding, as the base implementation is intentionally empty.
- **Never log or expose secrets in audit records, notifications, or logs.** Always use CIDs or other non-sensitive identifiers.
- Keep audit and notification logic efficient to avoid slowing down access operations.
- Document custom logic for maintainability and compliance.

---

## Summary

The `touch` method is a powerful tool for tracking, auditing, and automating responses to entity access. By customizing it, you can enhance security, compliance, and user experience in your application—from simple timestamping to advanced integrations.

---

*Last updated: 2025-11-14*
