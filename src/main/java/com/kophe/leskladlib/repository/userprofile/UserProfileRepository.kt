package com.kophe.leskladlib.repository.userprofile

import com.kophe.leskladlib.datasource.LeSkladUser
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.TaskResult

interface UserProfileRepository {

    suspend fun user(): LeSkladUser?

    //TODO: rename
    suspend fun tryLoginWithCachedUser(): TaskResult<Unit, LSError>

    suspend fun auth(username: String, email: String, password: String): TaskResult<Unit, LSError>

    suspend fun clearData()

    suspend fun checkWriteAccess(): Boolean

    suspend fun isUserAdmin(): Boolean
}
