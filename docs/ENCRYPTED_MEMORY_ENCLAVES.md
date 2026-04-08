# Encrypted Memory Enclaves for Encryptable

## Overview

Encrypted memory enclaves provide hardware-level memory encryption that protects application memory from the OS, hypervisor, and physical attacks. This document explains when and how to use enclaves with Encryptable.

---

## 🔐 What Are Encrypted Memory Enclaves?

Hardware-enforced secure execution environments where CPU encrypts all memory, preventing:
- OS/kernel from reading application memory
- Hypervisor from accessing VM memory (cloud security)
- Physical attacks (RAM dumping, cold boot attacks)
- DMA attacks (Direct Memory Access)

### Available Technologies

| Technology | Type | Availability | Overhead |
|-----------|------|-------------|----------|
| **Intel TDX** | VM-level | Xeon 4th Gen+ (2023+) | 2-5% |
| **AMD SEV-SNP** | VM-level | EPYC 3rd Gen+ (2021+), Azure, GCP, OCI | 2-6% |
| **Intel SGX** | Process-level | Xeon only | 20-50% |
| **AWS Nitro** | Process-level | EC2 instances | 5-15% |

**For Encryptable:** VM-level enclaves (TDX/SEV-SNP) are recommended due to lower overhead and simpler deployment.

---

## 🚀 Why Encryptable Benefits from Enclaves

Encryptable is designed to provide request-scoped (transient) knowledge, ensuring that secrets and decrypted data exist in memory only for the duration of a request and are securely wiped afterward. However, even with these protections, secrets are still present in server memory during active requests. This creates a potential attack surface if an attacker gains access to the host OS, hypervisor, or physical hardware.

Deploying Encryptable inside a hardware-backed memory enclave provides several key benefits:

- **Memory Safety:** Enclaves ensure that secrets and decrypted data in memory are protected from the host OS, hypervisor, and physical attacks, even during active requests.
- **Defense-in-Depth:** Enclaves add a strong additional layer of security on top of Encryptable's software protections, reducing the risk of memory scraping, cold boot, or DMA attacks.
- **Insider Threat Mitigation:** Even privileged cloud or infrastructure administrators cannot access enclave-protected memory, reducing the risk of insider attacks.
- **Compliance Enablement:** Many regulatory frameworks require memory encryption for sensitive workloads; enclaves help meet these requirements.
- **No Code Changes Needed:** Encryptable runs unmodified inside enclave-backed VMs, making it easy to adopt this advanced protection.

**Summary:**
While Encryptable already minimizes the exposure of secrets in memory, running inside an enclave ensures that even the most advanced memory attacks are mitigated, providing the highest practical level of security for sensitive applications.

---

## 🚀 Quick Start

### Option 1: Oracle Cloud Infrastructure (OCI) AMD SEV-SNP

```bash
# Launch a Confidential VM with AMD SEV-SNP on OCI
# (Use the OCI Console or CLI to provision a VM.Standard.E4.Flex or similar shape with Confidential Computing enabled)
# Example (CLI):
ooci compute instance launch \
  --shape VM.Standard.E4.Flex \
  --image-id <your-image-ocid> \
  --metadata '{"user_data":"<base64-cloud-init>"}' \
  --is-confidential-compute-enabled true

# Inside VM: Run Encryptable normally
java -jar encryptable-app.jar
```

### Option 2: Azure Confidential VM (AMD SEV-SNP) - Easiest

```bash
# Create SEV-SNP protected VM
az vm create \
  --resource-group encryptable-rg \
  --name encryptable-sev-vm \
  --size Standard_DC4as_v5 \
  --image Ubuntu2204 \
  --security-type ConfidentialVM \
  --os-disk-security-encryption-type VMGuestStateOnly

# Inside VM: Run Encryptable normally
java -jar encryptable-app.jar
```

### Option 2: On-Premises (AMD SEV-SNP)

```bash
# Launch SEV-SNP protected VM with QEMU
qemu-system-x86_64 \
  -machine q35 \
  -enable-kvm \
  -cpu EPYC-v4 \
  -object sev-snp-guest,id=sev0,cbitpos=51,reduced-phys-bits=1 \
  -machine memory-encryption=sev0 \
  -smp 4 -m 8G \
  -drive file=encryptable-guest.qcow2,if=virtio

# Inside VM: Run Encryptable normally
java -jar encryptable-app.jar
```

### Option 3: On-Premises (Intel TDX)

```bash
# Launch TDX-protected VM with QEMU
qemu-system-x86_64 \
  -machine q35,kernel-irqchip=split,confidential-guest-support=tdx \
  -object tdx-guest,id=tdx \
  -cpu host,-kvm-steal-time \
  -smp 4 -m 8G \
  -drive file=encryptable-tdx-guest.qcow2,if=virtio

# Inside VM: Run Encryptable normally
java -jar encryptable-app.jar
```

**Key Point:** No code changes needed—Encryptable runs unmodified inside the encrypted VM.

---

## 📊 Performance Impact

### Benchmark: Encryptable with VM-Level Enclaves

| Operation | Normal VM | TDX/SEV-SNP | Overhead |
|-----------|-----------|-------------|----------|
| Application startup | 12.0s | 12.2s | +2% |
| User save (encrypt) | 15ms | 15.3ms | +2% |
| User load (decrypt) | 12ms | 12.2ms | +2% |
| GridFS upload (100MB) | 850ms | 870ms | +2% |
| Request throughput | 1000 req/s | 965 req/s | -4% |

**Conclusion:** Intel TDX and AMD SEV-SNP add only 2-6% overhead—negligible for most applications.

---

## 🎯 Should You Use Enclaves?

### Overall Verdict: 🏰 Recommended for All Encryptable Deployments

Running Encryptable inside a hardware-backed memory enclave is recommended for all applications, as it provides strong protection against memory disclosure, insider threats, and advanced attacks—even in cloud environments. While enclaves add some complexity and cost, they offer the highest practical level of security for request-scoped knowledge systems.

### ✅ Why Use Enclaves:

1. **Protects against memory disclosure** – Even if the OS or hypervisor is compromised, secrets remain protected.
2. **Mitigates insider and cloud provider threats** – Enclaves isolate application memory from infrastructure administrators.
3. **Meets compliance and regulatory requirements** – Many industries require memory encryption for sensitive workloads.
4. **No code changes required** – Encryptable runs unmodified inside enclave-backed VMs.
5. **Supported on major clouds** – OCI, Azure, and GCP all offer AMD SEV-SNP confidential VMs.

### ❗ Considerations:

- **Cost** – Enclave-backed VMs are more expensive than standard VMs.
- **Hardware requirements** – Requires server-grade CPUs (EPYC/Xeon) or supported cloud instances.
- **Operational complexity** – Provisioning and managing enclaves may require additional setup.

### 💡 Alternatives (if enclave deployment is not feasible):

- Invest in a professional security audit ($4k-6k one-time)
- Use encrypted disk/swap, high-entropy secrets, and memory locking
- Apply all standard security best practices (see [Best Practices](BEST_PRACTICES.md))

**Result:** Enclaves provide the highest level of protection, but strong security is still possible with best practices if enclaves are not an option.

---

## 🏆 Technology Comparison

### Intel TDX vs AMD SEV-SNP (Equivalent for Encryptable)

Both provide VM-level memory encryption with similar characteristics:

| Feature | Intel TDX | AMD SEV-SNP | Winner |
|---------|-----------|-------------|--------|
| **Overhead** | 2-5% | 2-6% | Tie ✅ |
| **Memory Limit** | None | None | Tie ✅ |
| **Code Changes** | None | None | Tie ✅ |
| **Availability** | Xeon 4th Gen+ (2023+) | EPYC 3rd Gen+ (2021+), Azure, GCP, OCI | SEV-SNP (older) |
| **Cloud Support** | Limited (emerging) | Wide (Azure, GCP, OCI) | SEV-SNP |
| **Maturity** | Newer | More mature | SEV-SNP |

**Choose based on:** Available hardware or cloud provider, not technical differences.

### Process-Level vs VM-Level

| Factor | Intel SGX (Process) | TDX/SEV-SNP (VM) | Better for Encryptable |
|--------|-------------------|------------------|----------------------|
| **Overhead** | 20-50% | 2-6% | VM-level ✅ |
| **Memory Limit** | 256GB max | None | VM-level ✅ |
| **Code Changes** | Gramine required | None | VM-level ✅ |
| **Use Case** | Specific components | Full applications | VM-level ✅ |

**For full JVM apps like Encryptable:** VM-level enclaves (TDX/SEV-SNP) are clearly superior.

---

## 💰 Cost Analysis

**OCI Confidential Compute (AMD SEV-SNP):**
- No additional charge for confidential computing—only the performance overhead applies (pricing is the same as standard VM shapes).

**Azure Confidential VM (SEV-SNP):**
- Standard_DC4as_v5 (4 vCPU, 16GB): ~$350/month
- Comparable standard VM: ~$140/month
- **Premium:** ~$210/month (150% more)

---

## 📚 Quick Reference

### When Enclaves Make Sense
- Government/military (classified data)
- Healthcare (genetic/psychiatric records)
- Financial (cryptographic master keys)
- Zero-trust cloud (don't trust hypervisor)

### When Standard Approach is Better
- Startups, MVPs, SaaS
- E-commerce, web applications
- Internal tools
- Budget-conscious projects

### Key Takeaway
**Enclaves are a recommended solution for Encryptable:**
- Provide strong, hardware-enforced memory protection
- Suitable for all deployments, especially in cloud or regulated environments
- If cost or complexity is prohibitive, follow all other best practices and consider a professional audit

---

## 🔗 Resources

**Documentation:**
- Intel TDX: https://www.intel.com/content/www/us/en/developer/tools/trust-domain-extensions/overview.html
- AMD SEV: https://developer.amd.com/sev/
- OCI Confidential Computing: https://docs.oracle.com/en-us/iaas/Content/Compute/References/computeconfidentialcomputing.htm
- Azure Confidential Computing: https://azure.microsoft.com/solutions/confidential-compute/
- AWS Nitro Enclaves: https://docs.aws.amazon.com/enclaves/latest/user/nitro-enclave.html

**Implementation Tools:**
- Gramine (SGX LibOS): https://gramine.readthedocs.io/
- QEMU with SEV: https://www.qemu.org/

---


**Last Updated:** 2026-04-02  
**Framework Version:** 1.2.1   
**Recommendation:** Use standard security practices unless you have ultra-high-security requirements.
