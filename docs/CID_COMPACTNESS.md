# CID: A More Compact Alternative to UUID

a CID (Compact ID) is a 22-character, URL-safe Base64 encoding of 16 bytes (128 bits).\

It is designed as a modern, compact, and secure alternative to the traditional UUID (Universally Unique Identifier), which is typically represented as a 36-character hexadecimal string.

---

## 🌟 Why CID?

- **Compactness:**
  - **CID:** 22 characters (Base64 URL-safe, no padding)
  - **UUID:** 36 characters (hexadecimal with hyphens)
  - **Result:** CID is ~39% shorter than a UUID string, while providing the same 128 bits of entropy.

- **URL-Safe:**
  - CID uses Base64 URL-safe encoding (A-Z, a-z, 0-9, - and _), making it safe for use in URLs, filenames, and most text fields without encoding or escaping.
  - UUIDs use hexadecimal and hyphens, which are also URL-safe, but less compact.

- **No Padding:**
  - CID is always 22 characters, with no trailing = padding, making validation and storage easier.

- **Same Entropy as UUID:**
  - Both CID and UUID represent 128 bits of randomness or uniqueness.

- **Performance:**
  - CID encoding/decoding is fast and natively supported in Java/Kotlin via Base64.

- **Entropy Validation:**
  - All CID generation (including HKDF-derived and random) automatically validates entropy using Shannon entropy calculation (≥3.5 bits/character) and repetition checking (≥25% unique characters).
  - Prevents weak secrets like "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" while accepting high-quality Base64 URL-safe secrets.
  - Automatically regenerates if insufficient entropy is detected, ensuring all CIDs have cryptographic quality.

---

## 📊 Example Comparison

| Type  | Example Value                        | Length |
|-------|--------------------------------------|--------|
| UUID  | 123e4567-e89b-12d3-a456-426614174000 | 36     |
| CID   | Ej5FZ-ibEtOkVkJmFBdAAA               | 22     |

---

## 🔄 Interoperability

- **CID and UUID are both 128 bits:**
  - You can convert between CID and UUID without loss of information.
  - This allows you to use CID internally for compactness, and convert to UUID when needed for interoperability with external systems.

---

## 🧩 Why No Sections or Hyphens in CID?

- **UUIDs use hyphens to divide the string into sections** (8-4-4-4-12), which historically corresponded to different fields (time, version, node, etc.) in the UUID specification.
- **In modern usage, especially with random (UUIDv4) or cryptographically derived IDs, these sections are arbitrary** and do not carry meaningful information for most applications.
- **CIDs are designed for maximum compactness and randomness:**
  - All 128 bits are random (or derived from cryptographic functions), so there is no need to divide the string into sections.
  - Removing hyphens and using a continuous, URL-safe Base64 string makes CIDs shorter, easier to use in URLs, and more efficient for storage and transmission.
- **Summary:** For Encryptable and similar frameworks, the structure imposed by UUID hyphens is unnecessary. CIDs are fully random, and their compact, sectionless format is ideal for modern, secure, and user-friendly identifiers.

---

## 🚀 When to Use CID

- When you need a compact, URL-safe, and unique identifier.
- When you want to reduce storage or bandwidth for IDs.
- When you want to use IDs in URLs, QR codes, or filenames.
- When you want the same security and uniqueness guarantees as UUID, but in a shorter format.

---

## 🎬 Conclusion

CID is a modern, compact, and secure alternative to UUID. It is ideal for applications that require short, URL-safe, and unique identifiers, without sacrificing entropy or security. By using CID, you can reduce storage, improve readability in URLs, and maintain full compatibility with systems that use UUIDs.