# CID Collision Probability and Security Analysis

## 🎯 Executive Summary

This document analyzes the probability of accidental collisions between different secrets when using CID (Compact ID) for entity identification. CID is a 22-character, URL-safe Base64 encoding of 16 random bytes (128 bits), providing the same entropy as a UUID but in a more compact and URL-friendly format. Accidental collisions are astronomically rare in real-world usage.

---

## ⚠️ Important Clarification

**Two Types of ID Generation:**
1. **Intentional Deterministic Generation (By Design)** ✅
   - Same secret + same class = same CID
   - This is **NOT a collision**—it's the **primary feature**!
   - Enables entity lookups via cryptographic addressing
   - Example: `findBySecretOrNull(secret)` works because IDs are deterministic

2. **Accidental Natural Collision (Extremely Rare)** ⚠️
   - Different secrets accidentally produce the same CID
   - **This is what this document analyzes**
   - Birthday paradox: ~585,000 years @ 1M entities/sec with different secrets
   - Practically impossible in real-world usage

**Bottom Line:** If you're using the same secret to find an entity, you'll always get the same CID (that's the feature!). This analysis is about the astronomically rare chance that two DIFFERENT secrets randomly generate the same CID.

---

## 🎯 Question

**"If CID is 128 bits (16 bytes, 22 Base64 chars), and I write 1 million entities/second, how long until an CID collision?"**

**Answer:** Approximately **585,000 years** (50% collision probability)—but only if each entity uses a **different unique secret**.

---

## 📊 The Math

### CID Space

- **Size:** 16 bytes = 128 bits
- **Total possible CID:** 2^128 = 340,282,366,920,938,463,463,374,607,431,768,211,456
- **That's:** 340 undecillion possible CIDs

### Birthday Paradox Calculation

The birthday paradox tells us that collisions become likely at √(space size):

```
Collision threshold (50% probability): 2^64
= 18,446,744,073,709,551,616 entities
= 18.4 quintillion entities
```

**At 1 million entities per second:**
```
Time = 2^64 / 1,000,000 entities/sec
     = 18,446,744,073,709,551,616 / 1,000,000
     = 18,446,744,073,709 seconds
     = 584,942 years
```

**Result:** ~585,000 years to reach a 50% chance of collision at 1 million entities/sec.

---

## 🏆 Security Implications

- **Deterministic CIDs:** Enable cryptographic addressing and entity lookup by secret.
- **Collision Risk:** Accidental collisions between different secrets are astronomically rare.
- **Practical Safety:** For all practical purposes, collisions will not occur in any real-world deployment.
- **Data Safety Even on Collision:** Even if a collision were to occur (i.e., two different secrets produce the same CID), the data remains safe. This is because the secret used to generate and access the data is 32 bytes (256 bits) long—the key length for AES-256—and cryptographically unique. Only the correct secret can decrypt or access the associated data, so a collision in CID does not compromise data confidentiality or integrity.

---

## ❓ FAQ

**Question:**
"So should I check if an CID exists before saving an entity?"

**Answer:**
In practice, you do not need to check for CID existence before saving an entity when using deterministic, cryptographically derived CIDs.

**Justification:**
- The probability of accidental collision between different secrets is astronomically low (see above calculations).
- If a collision were to occur, the data would still be safe, as only the correct 32-byte (256-bit) secret can decrypt or access the data associated with that CID.
- The deterministic nature of CID means that the same secret will always produce the same CID, which is a feature for cryptographic addressing—not a risk for accidental overwrite.
- In real-world deployments, the risk of two different users generating the same CID is negligible, and the cryptographic isolation ensures data confidentiality and integrity even in the event of a collision.

**Summary:**
You are free to implement a check if your application requires absolute certainty or has additional business logic constraints, but for most use cases, it is not necessary due to the extremely low collision probability and the security guarantees of the system.

---

## 🎬 Conclusion

CID provides deterministic, collision-resistant, and URL-safe identifier generation. The risk of accidental collision is negligible, making it safe for use in high-throughput, security-sensitive applications.