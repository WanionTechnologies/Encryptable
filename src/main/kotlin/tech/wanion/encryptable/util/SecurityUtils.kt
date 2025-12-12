package tech.wanion.encryptable.util

import kotlin.math.ln

/**
 * Security utility functions for validating secrets and cryptographic inputs.
 */
object SecurityUtils {
    /**
     * Validates that a secret has sufficient entropy.
     *
     * Minimum requirements:
     * - At least 22 characters for @Id (22 chars = 16 bytes = 128 bits in Base64)
     * - At least 32 characters for @HKDFId (32 chars = 256 bits for enhanced security)
     * - Shannon entropy ≥ 3.5 bits per character (practical threshold)
     * - Not composed of repetitive patterns
     *
     * This validation is designed to accept high-quality Base64 URL-safe secrets
     * while rejecting weak secrets like "aaaaaaaaaaaaaaaaaaaaaa" or "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".
     *
     * @param secret The secret to validate
     * @param minimumLength Minimum character length (default: 22 for @Id, use 32 for @HKDFId)
     * @return true if the secret has sufficient entropy, false otherwise
     *
     * Example usage:
     * ```kotlin
     * // For @HKDFId (32 characters minimum)
     * val hkdfSecret = "dGVzdF9zZWNyZXRfd2l0aF9nb29kX2VudHJvcHlfeHl6"
     * if (SecurityUtils.hasMinimumEntropy(hkdfSecret, 32)) {
     *     // Use secret for HKDF derivation
     * }
     *
     * // For @Id (22 characters minimum)
     * val idSecret = "dGVzdF9zZWNyZXRfMTIz"
     * if (SecurityUtils.hasMinimumEntropy(idSecret, 22)) {
     *     // Use secret for random CID
     * }
     * ```
     */
    @JvmStatic
    fun hasMinimumEntropy(secret: String, minimumLength: Int = 22): Boolean {
        // Must meet minimum length requirement
        require(minimumLength >= 22) { "Minimum length must be at least 22 characters (22 chars = 128 bits for @Id, 32 chars = 256 bits for @HKDFId)" }
        if (secret.length < minimumLength) return false

        // Calculate Shannon entropy
        val entropy = calculateShannonEntropy(secret)

        // Minimum 3.5 bits per character (allows good Base64, rejects weak secrets)
        // Base64 theoretical max: ~6 bits/char (64 symbols = 2^6)
        // Good random Base64: ~5.5-6 bits/char
        // Threshold 3.5: Practical balance (not too strict, not too weak)
        if (entropy < 3.5) return false

        // Check for repetitive patterns (same character repeated)
        val uniqueChars = secret.toSet().size
        val repetitionRatio = uniqueChars.toDouble() / secret.length

        // Require at least 25% unique characters to avoid "aaaaaaaaaa..." patterns
        if (repetitionRatio < 0.25) return false

        return true
    }

    /**
     * Calculates Shannon entropy for a string.
     *
     * Shannon entropy measures the average information content per character.
     * Higher entropy = more randomness/unpredictability.
     *
     * Formula: H = -Σ(p(x) * log2(p(x))) where p(x) is probability of character x
     *
     * @param input The string to analyze
     * @return Shannon entropy in bits per character
     */
    @JvmStatic
    fun calculateShannonEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0

        // Count character frequencies
        val frequencies = mutableMapOf<Char, Int>()
        for (char in input) {
            frequencies[char] = frequencies.getOrDefault(char, 0) + 1
        }

        // Calculate Shannon entropy
        val length = input.length.toDouble()
        var entropy = 0.0

        for (count in frequencies.values) {
            val probability = count / length
            entropy -= probability * (ln(probability) / ln(2.0))
        }

        return entropy
    }
}

