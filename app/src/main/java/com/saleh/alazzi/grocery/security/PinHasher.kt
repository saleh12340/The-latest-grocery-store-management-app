package com.saleh.alazzi.grocery.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinCredential(val saltBase64: String, val hashBase64: String)

object PinHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun create(pin: CharArray): PinCredential {
        validate(pin)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(pin, salt)
        return PinCredential(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(derived)
        )
    }

    fun verify(pin: CharArray, credential: PinCredential): Boolean {
        if (pin.size !in 4..8 || pin.any { !it.isDigit() }) return false
        return runCatching {
            val salt = Base64.getDecoder().decode(credential.saltBase64)
            val expected = Base64.getDecoder().decode(credential.hashBase64)
            MessageDigest.isEqual(expected, derive(pin, salt))
        }.getOrDefault(false)
    }

    private fun validate(pin: CharArray) {
        require(pin.size in 4..8 && pin.all { it.isDigit() }) {
            "يجب أن يتكون PIN من 4 إلى 8 أرقام"
        }
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
