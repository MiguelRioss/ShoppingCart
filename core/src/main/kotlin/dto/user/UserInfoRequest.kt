package dto.user

import domain.user.User

/** Internal wrapper used when producing an authenticated user profile response. */
data class UserInfoRequest(
    val user: User
)
