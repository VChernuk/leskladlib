package com.kophe.leskladlib.datasource.currentusersource

import com.kophe.leskladlib.datasource.LeSkladUser
import com.kophe.leskladlib.datasource.LeSkladUserDao
import com.kophe.leskladlib.logging.Logger
import com.kophe.leskladlib.logging.LoggingUtil

//TODO: review if needed
interface CurrentUserSource {

    suspend fun currentUser(): LeSkladUser?

    fun saveUser(user: LeSkladUser)

    fun logout()

}

class DefaultCurrentUserSource(
    private val userDao: LeSkladUserDao, override val loggingUtil: LoggingUtil
) : CurrentUserSource, Logger {

    private var cachedUser: LeSkladUser? = null

    override suspend fun currentUser(): LeSkladUser? = cachedUser ?: userDao.selectCurrentUser()

    override fun saveUser(user: LeSkladUser) {
        log("saveUser(...)")
        userDao.clear()
        cachedUser = user
        userDao.insertUser(user)
    }

    override fun logout() {
        log("logout()")
        cachedUser = null
        userDao.clear()
    }

}
