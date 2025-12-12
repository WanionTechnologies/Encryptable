package cards.project.mongo

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tech.wanion.encryptable.util.AES256
import tech.wanion.encryptable.util.HKDF
import java.util.Base64
import kotlin.collections.plusAssign

class CryptoPropertiesTest : BaseEncryptableTest() {

    private val secret = "A".repeat(32) // Framework enforces 32+ for @HKDFId; use randomSecret() in real flows
    private val source: Any = this::class.java // any stable context/class is fine

    @Test
    fun `AES-GCM round-trip returns original plaintext`() {
        val plaintext = "hello, world!".toByteArray()
        val enc = AES256.encrypt(secret, source, plaintext)
        // Sanity: ciphertext must differ from plaintext and be longer due to IV+tag
        Assertions.assertFalse(enc.contentEquals(plaintext), "Ciphertext must not equal plaintext")
        val expectedLen = plaintext.size + 12 /* IV */ + 16 /* GCM tag */
        Assertions.assertEquals(expectedLen, enc.size, "Ciphertext length should be IV(12)+cipher+tag(16)")

        val dec = AES256.decrypt(secret, source, enc)
        Assertions.assertArrayEquals(plaintext, dec, "Round-trip must return original plaintext")
    }

    @Test
    fun `AES-GCM tamper causes auth failure per framework semantics`() {
        val plaintext = "tamper-me".toByteArray()
        val enc = AES256.encrypt(secret, source, plaintext).copyOf()

        // Flip 1 byte in ciphertext (not in IV). IV is first 12 bytes for AES-GCM.
        val ivLen = 12
        val tamperIndex = ivLen + 3 // within ciphertext
        require(tamperIndex < enc.size) { "Encrypted buffer unexpectedly small for tamper test" }
        enc[tamperIndex] = (enc[tamperIndex].toInt() xor 0x01).toByte()

        val dec = AES256.decrypt(secret, source, enc)

        // Framework behavior on decrypt failure: returns the original encrypted data (still-encrypted payload)
        Assertions.assertFalse(dec.contentEquals(plaintext), "Tamper should not yield plaintext")
        Assertions.assertTrue(
            dec.contentEquals(enc),
            "On failure, decrypt returns still-encrypted payload (tampered buffer)"
        )
    }

    @Test
    fun `AES-GCM IV uniqueness across runs`() {
        val plaintext = "same".toByteArray()
        val runs = 200
        val ivSet = mutableSetOf<String>()
        repeat(runs) {
            val enc = AES256.encrypt(secret, source, plaintext)
            val iv = enc.copyOfRange(0, 12) // first 12 bytes are IV
            ivSet += Base64.getEncoder().encodeToString(iv)
        }
        Assertions.assertEquals(runs, ivSet.size, "IV reuse detected (IV must be unique per encryption)")
    }

    @Test
    fun `AES-GCM multiple encryptions produce distinct ciphertexts and decrypt to same plaintext`() {
        val plaintext = "hello randomness".toByteArray()
        val runs = 1000

        val ivSet = mutableSetOf<String>()
        val ctSet = mutableSetOf<String>()
        val encList = mutableListOf<ByteArray>()

        repeat(runs) {
            val enc = AES256.encrypt(secret, source, plaintext)
            encList += enc
            val iv = enc.copyOfRange(0, 12)
            ivSet += Base64.getEncoder().encodeToString(iv)
            ctSet += Base64.getEncoder().encodeToString(enc)
        }

        // Expect all IVs unique and ciphertexts distinct
        Assertions.assertEquals(runs, ivSet.size, "IV reuse detected (must be unique per encryption)")
        Assertions.assertEquals(runs, ctSet.size, "Ciphertexts should differ across encryptions due to random IVs")

        // All decrypt to the original plaintext with the same secret
        encList.forEach { enc ->
            val dec = AES256.decrypt(secret, source, enc)
            Assertions.assertArrayEquals(plaintext, dec, "Each ciphertext must decrypt to the original plaintext")
        }
    }

    @Test
    fun `AES-GCM decrypt with wrong secret returns still-encrypted payload`() {
        val plaintext = "wrong-secret case".toByteArray()
        val enc = AES256.encrypt(secret, source, plaintext)
        val wrongSecret = "B".repeat(32) // valid length but not the same secret

        val decWithWrong = AES256.decrypt(wrongSecret, source, enc)

        // Expectation: not plaintext; returns the original encrypted buffer per framework semantics
        Assertions.assertFalse(decWithWrong.contentEquals(plaintext), "Wrong secret should not yield plaintext")
        Assertions.assertTrue(
            decWithWrong.contentEquals(enc),
            "On wrong secret, decrypt returns still-encrypted payload"
        )
    }

    @Test
    fun `HKDF determinism and namespacing`() {
        val s1 = "Z".repeat(32)
        val c1 = this::class.java
        val c2 = String::class.java

        val k1a = HKDF.deriveFromEntropy(s1, c1, "TEST_CONTEXT")
        val k1b = HKDF.deriveFromEntropy(s1, c1, "TEST_CONTEXT")
        Assertions.assertArrayEquals(k1a, k1b, "HKDF must be deterministic for same (secret, class)")

        val k2 = HKDF.deriveFromEntropy(s1, c2, "TEST_CONTEXT")
        Assertions.assertFalse(k1a.contentEquals(k2), "HKDF outputs must differ for different classes (info parameter)")

        val cidBytes = HKDF.deriveFromEntropy(s1, c1, "TEST_CONTEXT", 16)
        Assertions.assertEquals(16, cidBytes.size, "CID derivation must be 16 bytes")
        Assertions.assertEquals(32, k1a.size, "AES key derivation must be 32 bytes")
    }
}