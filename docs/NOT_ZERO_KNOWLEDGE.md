### 🚫 Not Strictly Zero-Knowledge (But As Close As Possible for Backend-Only)

Encryptable is not a strict zero-knowledge system.  
While it achieves request-scoped (transient) knowledge—meaning secrets and keys are only present in memory for the duration of a request or operation—there are brief periods where the server does have access to user secrets in memory.  
This is a necessary trade-off for practical backend-only applications, and Encryptable is designed to minimize this exposure as much as possible.

We apologize for previously stating that Encryptable was zero-knowledge.  
Our documentation and messaging have been updated to clarify that, while Encryptable is not zero-knowledge in the strict cryptographic sense, it is as close as possible for backend-only architectures. 
For true zero-knowledge, secrets must never be present on the server at any time, which is only achievable with client-side cryptography.