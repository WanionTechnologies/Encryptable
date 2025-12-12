# Encryptable: Example Gallery – Practical Usage Patterns

## 🏆 Executive Summary

This directory showcases practical examples of Encryptable, covering core features, advanced patterns, and best practices for secure, privacy-first data management in Kotlin and Spring Boot.

---

## 📚 Examples Overview

| Example                | Description                                                          |
|------------------------|----------------------------------------------------------------------|
| **01_BasicUsage**      | Simple entity with encrypted fields                                  |
| **02_NestedEntities**  | Parent-child relationships with encryption                           |
| **03_GridFS**          | Working with large binary fields                                     |
| **04_Lists**           | Managing lists of encrypted entities                                 |
| **05_AdvancedPatterns**| Complex scenarios and best practices                                 |
| **06_DerivedSecretsWiping** | Deriving secrets from credentials and marking them for secure wiping |

---

## ⚙️ Prerequisites

- MongoDB running locally (`localhost:27017`)
- Spring Boot application configured with MongoDB
- High-entropy secret generation mechanism

---

## 🚀 How to Run the Examples

Each example is a standalone Kotlin file with detailed comments. To use them:
1. Copy the entity and repository definitions into your project
2. Ensure your Spring Boot application has the Encryptable dependencies
3. Run the example code in a service or controller with proper secret management

---

## 🛡️ Important Notes & Best Practices

| Practice                | Recommendation                                  |
|-------------------------|-------------------------------------------------|
| Secret management       | ❌ Never hardcode secrets                        |
| Secret entropy          | ✅ Use at least 256 bits for production secrets  |
| Transaction support     | ✅ Ensure MongoDB supports transactions          |

---

*For more technical details, see the main documentation and individual example files.*