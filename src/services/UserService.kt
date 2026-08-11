package services

import domain.User
import java.util.UUID

interface UserService {
    fun registerUser(email: String, password: String): User
    fun getUser(userId: UUID): User?
}
