package com.kophe.leskladlib.repository.userprofile

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldPath.documentId
import com.google.firebase.firestore.Source.CACHE
import com.google.firebase.firestore.Source.DEFAULT
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.connectivity.ConnectionStateMonitor
import com.kophe.leskladlib.datasource.LeSkladUser
import com.kophe.leskladlib.datasource.currentusersource.CurrentUserSource
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import kotlinx.coroutines.tasks.await

class DefaultUserProfileRepository(
    currentUserSource: CurrentUserSource,
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val connection: ConnectionStateMonitor
) : CachedUserRepository(currentUserSource, loggingUtil), UserProfileRepository {

    private val auth by lazy { Firebase.auth }
    private val db by lazy { Firebase.firestore }
    private val admins by lazy { db.collection(builder.adminsCollection) }
    private val users by lazy { db.collection(builder.usersCollection) }
    private var cachedWriteAccess: Boolean? = null
    private var cachedIsUserAdmin: Boolean? = null

    override suspend fun clearData() {
        log("clearData(...)")
        auth.signOut()
        currentUserSource.logout()
        cachedIsUserAdmin = null
        cachedWriteAccess = null
    }

    override suspend fun tryLoginWithCachedUser(): TaskResult<Unit, LSError> = cachedUser()?.let {
        log("cached user found, will try to auth")
        if (auth.currentUser != null && currentUserSource.currentUser() != null) TaskSuccess()
        else TaskError()
    } ?: run {
        log("failed to login with cached user: no cached users found")
        TaskError()
    }

    override suspend fun auth(
        username: String, email: String, password: String
    ): TaskResult<Unit, LSError> = try {
        log("auth(...)")
        val task = auth.signInWithEmailAndPassword(email, password).await()
        log("logged in: ${task.user != null}")
        if (cachedUser()?.login != username) {
            log("clear old data on new user login")
            currentUserSource.saveUser(LeSkladUser(username, email))
        }
        TaskSuccess()
    } catch (e: java.lang.Exception) {
        log("failed to auth(...) due to ${e.message}")
        TaskError(SimpleError(e.message))
    }

    override suspend fun user() = cachedUser()

    //TODO: logging
    override suspend fun checkWriteAccess() = cachedWriteAccess ?: isUserAdmin() || try {
        log("checkWriteAccess(...)")
        val user = users.whereEqualTo(
            documentId(), user()?.email ?: throw Exception("no user email found")
        ).get(if (connection.available() != true) CACHE else DEFAULT).await()
        cachedWriteAccess = !user.isEmpty
        !user.isEmpty
    } catch (e: Exception) {
        log("failed to checkWriteAccess(...) due to ${e.message}")
        false
    }

    override suspend fun isUserAdmin() = cachedIsUserAdmin ?: try {
        log("isUserAdmin(...)")
        val user = admins.whereEqualTo(
            documentId(), user()?.email ?: throw Exception("no user email found")
        ).get(if (connection.available() != true) CACHE else DEFAULT).await()
        cachedIsUserAdmin = !user.isEmpty
        !user.isEmpty
    } catch (e: Exception) {
        log("failed to isUserAdmin(...) due to ${e.message}")
        false
    }
}
