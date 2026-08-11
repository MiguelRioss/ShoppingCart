package services

import org.mindrot.jbcrypt.BCrypt

/**
 * Hashes and verifies passwords.
 */
class PasswordHasher {
    /**
     * Hashes a plain text password with BCrypt.
     *
     * @param password plain text password
     * @return salted BCrypt hash
     */
    fun hash(password: String): String =
        BCrypt.hashpw(password, BCrypt.gensalt())

    /**
     * Checks whether a plain text password matches a stored hash.
     *
     * @param password plain text candidate password
     * @param passwordHash stored hash to compare against
     * @return true when the hashes match
     */
    fun matches(password: String, passwordHash: String): Boolean =
        BCrypt.checkpw(password, passwordHash)
}
