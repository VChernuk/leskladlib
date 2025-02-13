package com.kophe.leskladlib.repository.userprofile

import com.kophe.leskladlib.datasource.LeSkladUser
import com.kophe.leskladlib.datasource.currentusersource.CurrentUserSource
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository


abstract class CachedUserRepository(
    protected val currentUserSource: CurrentUserSource, loggingUtil: LoggingUtil
) : BaseRepository(loggingUtil) {

    protected suspend fun cachedUser(): LeSkladUser? = currentUserSource.currentUser()

}
