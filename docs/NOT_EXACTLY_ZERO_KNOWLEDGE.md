## Encryptable: Zero-Knowledge and Transient Knowledge

Encryptable implements a dual-scope security model:

- **True zero-knowledge outside of requests:** The server never persists user secrets. All cryptographic material is wiped from memory after each request, and the server cannot access or recover user data without the secret.
- **Transient knowledge during requests:** For the duration of a request, Encryptable temporarily holds the secret in memory to perform cryptographic operations (encryption, decryption, etc.) on behalf of the user. Once the request completes, all secrets are securely wiped from memory, restoring the zero-knowledge state.

This approach ensures:
- No secrets are ever persisted or accessible outside of the request context
- All cryptographic operations are performed securely and only when authorized by the user’s active session
- The zero-knowledge guarantee is maintained outside of active requests

**Implication:**
If a user loses their secret, the server cannot recover their data. Any recovery mechanism (such as recovery codes) must be explicitly implemented and managed by the application, and even then, the server never has access to the secret outside of the request context.

### Achieving (As Close As Possible to) Zero-Knowledge with Memory Enclaves

If Encryptable is deployed within a secure memory enclave (such as Intel SGX, AWS Nitro Enclaves, Oracle Cloud Infrastructure (OCI) AMD Secure Enclaves, or similar trusted execution environments), and all best practices are strictly followed (never logging secrets, ensuring no memory leaks, disabling core dumps, and using secure memory wiping), then it is possible to achieve a state as close as possible to zero-knowledge even during requests. In this scenario, secrets are only ever present in protected memory, inaccessible to the host OS or administrators, and never leave the enclave boundary.

**Best practices for (almost) zero-knowledge operation:**
- Run Encryptable inside a hardware-backed memory enclave (Intel SGX, AWS Nitro, OCI AMD Secure Enclaves, etc.)
- Never log secrets or sensitive data
- Disable core dumps and memory swapping
- Use secure memory wiping for all cryptographic material
- Regularly audit code and operational procedures for leaks

See [Best Practices](BEST_PRACTICES.md) for more details on secure memory handling and operational safeguards.

With these measures, Encryptable can be considered as close as possible to zero-knowledge not only outside of requests, but also during active requests, as secrets are never exposed to the host or any unauthorized party. Absolute zero-knowledge remains a theoretical ideal, but this approach achieves the highest practical standard currently possible.
