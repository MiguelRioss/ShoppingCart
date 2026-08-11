package services

import java.security.MessageDigest

class PasswordHasher {
    fun hash(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(password: String, passwordHash: String): Boolean =
        hash(password) == passwordHash
}
