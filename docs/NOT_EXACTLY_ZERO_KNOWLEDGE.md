## Encryptable: Zero-Knowledge and Transient Knowledge

Encryptable implements a dual-scope security model:

- **Zero-knowledge outside of requests:** The server never persists user secrets. All cryptographic material is wiped from memory after each request. The server has no knowledge of any secret and cannot access or recover user data without it.
- **Transient knowledge during requests:** For the duration of a request, Encryptable temporarily holds the secret in memory to perform cryptographic operations (encryption, decryption, etc.) on behalf of the user. Once the request completes, all secrets are securely wiped from memory, restoring the zero-knowledge state.

This approach ensures:
- No secrets are ever persisted or accessible outside of the request context
- All cryptographic operations are performed securely and only when authorized by the user's active session
- The zero-knowledge guarantee is maintained outside of active requests

**Implication:**
If a user loses their secret, the server cannot recover their data. Any recovery mechanism (such as recovery codes) must be explicitly implemented and managed by the application, and even then, the server never has access to the secret outside of the request context.

### Why Transient Knowledge?

**Transient knowledge is a deliberate design choice.**  
Pure zero-knowledge during requests would require the client to perform all cryptographic operations locally, making server-side features like relationship management, cascade delete, change detection, and storage handling impossible.  
Transient knowledge is the necessary bridge that enables practical, full-featured back-end development while maintaining the strongest security guarantees the server-side model allows.

**In other words**: without transient knowledge, Encryptable would be a client-side library, not a server-side framework.

### Hardware-Isolated Transient Knowledge with Memory Enclaves

If Encryptable is deployed within a secure memory enclave (such as Intel SGX, AWS Nitro Enclaves, Oracle Cloud Infrastructure (OCI) AMD Secure Enclaves, or similar trusted execution environments), and all best practices are strictly followed (never logging secrets, ensuring no memory leaks, disabling core dumps, and using secure memory wiping), the transient knowledge during requests becomes **hardware-isolated transient knowledge**. In this scenario, secrets are only ever present in protected memory, inaccessible to the host OS or administrators, and never leave the enclave boundary.

This means Encryptable's security model with memory enclaves can be described as:
- **Zero-knowledge outside of requests** — no secret material exists anywhere on the server
- **Hardware-isolated transient knowledge during requests** — secrets exist only inside protected enclave memory, inaccessible to the host, OS, or any unauthorized party.
- **This is as close as practically possible to zero-knowledge even during active requests**.

**Best practices for hardware-isolated transient knowledge:**
- Run Encryptable inside a hardware-backed memory enclave (Intel SGX, AWS Nitro, OCI AMD Secure Enclaves, etc.)
- Never log secrets or sensitive data
- Disable core dumps and memory swapping
- Use secure memory wiping for all cryptographic material
- Regularly audit code and operational procedures for leaks

See [Best Practices](BEST_PRACTICES.md) for more details on secure memory handling and operational safeguards.
