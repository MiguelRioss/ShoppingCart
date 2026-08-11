package db.offline

import db.UserRepository
import domain.User
import java.util.UUID

class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<UUID, User>()

    override fun saveUser(user: User): User {
        users[user.id] = user
        return user
    }

    override fun getUser(userId: UUID): User? = users[userId]

    override fun getUserByEmail(email: String): User? =
        users.values.firstOrNull { it.email == email }
}
