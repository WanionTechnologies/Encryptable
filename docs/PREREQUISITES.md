# 📝 Encryptable: System & Runtime Prerequisites

## 🌟 Executive Summary

Encryptable requires a modern JVM environment and specific runtime configuration to ensure security, performance, and compatibility. This document lists all technical prerequisites for running Encryptable securely and efficiently.

---

## 🏷️ Minimum Java Version

- **Java 21 or higher** is required.
  - Virtual threads (Project Loom) are used for concurrency.

---

## 🧩 Dependencies

The following dependencies are required for Encryptable.  
**All of these are included in the `encryptable-starter` package, so you do not need to add them manually.**

| Dependency | Version | Purpose |
|------------|---------|---------|
| org.jetbrains.kotlin:kotlin-stdlib | 2.2.21 | Kotlin standard library |
| org.jetbrains.kotlin:kotlin-reflect | 2.2.21 | Kotlin reflection support |
| org.springframework.boot:spring-boot-starter-webmvc | 4.0.0 | Spring Boot web framework |
| org.springframework.boot:spring-boot-starter-data-mongodb | 4.0.0 | MongoDB persistence layer |
| at.favre.lib:hkdf | 2.0.0 | HKDF key derivation (RFC 5869) |
| org.aspectj:aspectjrt | 1.9.25 | AspectJ runtime |
| org.aspectj:aspectjweaver | 1.9.25 | AspectJ weaving (AOP) |

> **Note:** These versions are based on the current release and may be updated in future versions. Always check the latest starter for up-to-date versions.

---

## ⚙️ Gradle Configuration

### Gradle Plugins

The following Gradle plugins are required for Encryptable to function correctly:

```kotlin
id("org.springframework.boot") version "4.0.0"
id("io.spring.dependency-management") version "1.1.7"
id("io.freefair.aspectj.post-compile-weaving") version "9.0.0"
```

_These plugin versions match the current release. They may be updated in future versions of Encryptable, so always check the latest documentation or starter for updates._

### Gradle Dependencies

It is strongly recommended to use the Encryptable Starter, which automatically includes all required dependencies in compatible versions.

To add the Encryptable Starter to your project, use:

```kotlin
dependencies {
    implementation("tech.wanion:encryptable-starter:1.0.4")
    aspect("tech.wanion:encryptable-starter:1.0.4")
}
```

---

## ⚙️ Required Gradle Tasks Configuration

To ensure Encryptable runs correctly in your development environment, add the following configuration to your `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()

    // Enable AspectJ load-time weaving for tests, using our aop.xml.
    val aspectjWeaver = configurations.testRuntimeClasspath.get().files.find { it.name.contains("aspectjweaver") }
    if (aspectjWeaver != null) {
        jvmArgs("-javaagent:${aspectjWeaver.absolutePath}")
        // Point LTW to our test aop.xml that limits weaving scope to our packages
        systemProperty("org.aspectj.weaver.loadtime.configuration","META-INF/aop.xml")
        // Optional: reduce noise from missing types in third-party libs
        systemProperty("org.aspectj.weaver.DUMP.before","false")
    }

    // Add JVM arguments to open javax.crypto.spec and java.lang for reflection during tests
    jvmArgs(
        "--add-opens", "java.base/javax.crypto.spec=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
}

tasks.withType<JavaExec> {
    // Add JVM arguments to open javax.crypto.spec and java.lang for reflection for all JavaExec tasks
    jvmArgs(
        "--add-opens", "java.base/javax.crypto.spec=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
}
```

_This configuration auto-applies the necessary JVM arguments for tests and development. In production, you must add the required arguments manually._

---

## ⚙️ Required JVM Arguments

To enable secure reflection and memory clearing, add the following JVM arguments to all JavaExec and Test tasks:

```shell
--add-opens java.base/javax.crypto.spec=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
```

- These arguments allow Encryptable to clear internal fields of `SecretKeySpec` and `String` objects.
- Without these, you will encounter `InaccessibleObjectException` when clearing secrets, and the application will refuse to start.
- **Encryptable will not run unless these arguments are set. This is a deliberate fail-fast security feature: if the arguments are missing, Encryptable will refuse to start, ensuring that security is never silently compromised.**
- For development and testing, these arguments are auto-configured by the Gradle tasks. In production, you must add them manually.

---

## 🧹 Mark All Derived Content for Cleaning

Any data or object that is derived from secrets or encrypted content (for example, decrypted values, processed keys, or temporary buffers) must be explicitly marked for cleaning. This ensures that all sensitive information is securely wiped from memory as soon as it is no longer needed, reducing the risk of memory exposure or leaks.

- This applies to all decrypted, derived, or intermediate content, not just the original secrets.
- Marking derived content for cleaning is essential for compliance with Encryptable's security guarantees and for passing security audits.
- Review your code and integrations to ensure that all such data is properly handled and cleaned up.

**At the end of each request, all data that has been marked with `markForWiping` will be securely cleaned (zerified) from memory by the framework. This guarantees that no sensitive or derived data remains accessible after the request is processed.**

**Example:**

```kotlin
val decrypted: ByteArray = AES256.decrypt(secretKey, this::class.java, someEncryptedData)
markForWiping(decrypted)
// Use the decrypted data
```

**Note**: Any decrypted data from `AES256.decrypt` is automatically marked for wiping. However, if you derive new data from it (e.g., parsing, processing), you must mark that new data for wiping as well.

---

## 🗄️ Recommended: MongoDB Context with @Transactional Support

For advanced use cases requiring multi-document transactions or atomic operations, it is recommended to use a MongoDB context (such as a replica set or sharded cluster) that supports the `@Transactional` annotation.

- This enables the use of Spring's `@Transactional` for atomic operations across multiple documents or collections.
- Not required for most use cases, but beneficial for complex business logic or strict consistency requirements.
- **Using transactions is important to prevent errors and avoid leaving orphaned or inconsistent data if something goes wrong during a multi-document operation.**
- See the [MongoDB Transactions documentation](https://docs.mongodb.com/manual/core/transactions/) for setup and configuration details.

---

## 🏁 Quick Checklist

- [x] Java 21+ installed
- [x] Required dependencies present (or use the Encryptable starter)
- [x] Required Gradle plugins configured (see section above)
- [x] Required Gradle tasks configuration added (see section above)
- [x] JVM args for reflection set (see section above)
- [x] MongoDB running
- [x] Spring Boot configured

---

## 🛡️ Security & Memory Enclave (Optional)

For ultra-high-security deployments, consider running the JVM in a hardware-backed encrypted memory enclave:
- **Intel SGX/TDX** or **AMD SEV-SNP**
- This ensures all memory is encrypted at the hardware level.
- Adds 2–50% performance overhead; recommended only for government, military, or compliance-mandated environments.

For details on hardware-backed encrypted memory enclaves, see the dedicated guide: [Encrypted Memory Enclaves](ENCRYPTED_MEMORY_ENCLAVES.md).

---

## ✅ Summary

Meeting these prerequisites ensures Encryptable runs securely, efficiently, and with full feature support.\
Always verify your environment before deploying to production.