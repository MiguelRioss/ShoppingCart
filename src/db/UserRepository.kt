package db

import domain.User
import java.util.UUID

interface UserRepository {
    fun saveUser(user: User): User
    fun getUser(userId: UUID): User?
    fun getUserByEmail(email: String): User?
}
