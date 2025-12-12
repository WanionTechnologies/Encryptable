# Using Encryptable Securely Without Professional Audit

## TL;DR - Quick Answer

**YES, you can use without a professional audit for:**
- ✅ Personal/hobby projects, learning
- ✅ Internal tools (non‑sensitive)
- ✅ MVPs/prototypes and small apps (with community review)

**NO, not without an audit for:**
- 🔴 Financial/payment data (PCI‑DSS)
- 🔴 Healthcare/PHI (HIPAA)
- 🔴 Government/defense, high‑liability apps
- 🔴 Large public apps with sensitive data

---

## Your Options (Pick One)

1) 🟢 Community review (FREE)
- Post to r/crypto, r/netsec, OWASP forums
- Ask on Crypto/Security StackExchange
- Expect feedback in ~2–4 weeks

2) 🟡 Self‑audit + expert consult ($1k–5k)
- Use SECURITY_AUDIT.md as a checklist
- 5–10h with a crypto expert for focused questions
- Add automated scans (ZAP, CodeQL)

3) 💰 Professional audit ($4k–6k)
- Full cryptographic/architecture review
- Best for compliance and enterprise trust

---

## Mandatory Security Practices (FREE)

- ✅ Industry‑standard crypto (AES‑GCM, HKDF) — already in framework
- ✅ Rate limiting + account lockout (3–5 failures)
- ✅ HTTPS/TLS everywhere; enforce TLS 1.2+ (prefer TLS 1.3)
- ✅ Monitoring/alerting + structured logs
- ✅ Dependency and OS patching; backups tested

Strong secrets
- Enforce minimum 32 characters for @HKDFId secrets (256 bits) and 22 characters for @Id (128 bits), automatically validated for entropy ≥3.5 bits/char
- Encourage 50+ chars and high entropy; reject common/weak secrets
- Add an entropy check in your app (e.g., zxcvbn or Shannon entropy)
- Note: @Id CIDs are 22 URL‑safe Base64 chars and are not secrets

Crypto failure handling (framework behavior)
- On encryption failure: framework returns an empty byte array → alert to avoid silent data loss
- On decryption failure: framework returns the still‑encrypted payload → treat as error; do not parse

Security testing
- Use OWASP ZAP, Burp Community, CodeQL, Dependabot

What to monitor
- Failed logins, rate‑limit hits, unusual patterns
- Encryption/decryption errors in logs
- Unexpected empty payloads on encrypt (encryption failure)
- "Still‑encrypted" payloads on decrypt (decryption failure)
- Slow queries and abnormal DB access

MongoDB hardening
- RBAC on, TLS on, auth on, JS disabled; restrict network access; audit logging on; frequent backups; stay updated

---

## Risk by Use Case

- ✅ LOW (Use Freely): personal, learning, internal tools
- 🟡 MEDIUM (Conditional): public apps with non‑sensitive data — complete checklist + community review
- 🔴 HIGH (Don’t Use Without Audit): payment/health data, government, high‑liability, large‑scale sensitive

---

## Community Review — Quick How‑To
- Share repo, SECURITY_AUDIT.md, and an architecture diagram
- Ask concrete questions (HKDF mode, secret policy, ZK architecture)
- Engage, iterate, document changes

---

## Self‑Audit Checklist (Short)

### Cryptography
- [x] AES‑256‑GCM and HKDF (RFC 5869)
- [x] Unique IVs via SecureRandom
- [x] 32+ char secrets enforced for @HKDFId
- [x] No secret equality checks in framework (timing‑safe by design)
- [ ] If app compares secrets, use constant‑time compare
- [x] No secrets in logs

### Application & Ops
- [ ] Rate limiting + lockout working
- [ ] HTTPS enforced; TLS 1.2+ (prefer TLS 1.3)
- [ ] Monitoring/alerts live; logs reviewed
- [ ] Incident response + backups tested
- [ ] Least privilege for DB and servers

---

## Bottom Line
- The framework’s crypto is sound; your job is to use it securely.
- For most non‑regulated apps: complete the checklist + community review.
- For regulated data or high‑liability apps: get a professional audit.

---

## Resources
- OWASP ZAP: https://www.zaproxy.org/
- CodeQL: https://codeql.github.com/
- Burp Suite Community: https://portswigger.net/burp/communitydownload
- Let’s Encrypt: https://letsencrypt.org/
- Bucket4j (rate limiting): https://github.com/vladimir-bukhtoyarov/bucket4j
- NoSQLMap: https://github.com/codingo/NoSQLMap
- MongoDB Security Checklist: https://www.mongodb.com/docs/manual/administration/security-checklist/
