package services

import domain.AuthToken
import domain.User

interface AuthService {
    fun login(email: String, password: String): AuthToken
    fun getUserFromBearerToken(bearerToken: String): User?
}
