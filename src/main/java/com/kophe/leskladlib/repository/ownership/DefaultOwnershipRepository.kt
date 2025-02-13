package com.kophe.leskladlib.repository.ownership

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.connectivity.ConnectionStateMonitor
import com.kophe.leskladlib.datasource.firestore.FirestoreCategory
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonEntry
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.OwnershipType
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import kotlinx.coroutines.tasks.await

class DefaultOwnershipRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val connection: ConnectionStateMonitor
) : OwnershipRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreOwnershipTypes by lazy { db.collection(builder.ownershipTypesCollection) }
    private val localCachedOwnershipTypes = mutableSetOf<OwnershipType>()

    override suspend fun precacheValues() {
        if (localCachedOwnershipTypes.isEmpty()) allOwnershipTypes()
    }

    override suspend fun allOwnershipTypes(forceReload: Boolean): TaskResult<List<OwnershipType>, LSError> =
        try {
            if (!forceReload && localCachedOwnershipTypes.isNotEmpty()) TaskSuccess(
                localCachedOwnershipTypes.toList()
            )
            else TaskSuccess(firestoreOwnershipTypes.get(if (connection.available() != true) Source.CACHE else Source.DEFAULT)
                .await().mapNotNull { result ->
                    log("${result.id} => ${result.data}")
                    val item = result.toObject<FirestoreCategory>()
                    log("ownership type: $item")
                    val title = item.title ?: return@mapNotNull null
                    val type = OwnershipType(title, result.id)
                    localCachedOwnershipTypes.add(type)
                    type
                })
        } catch (e: Exception) {
            log("allOwnershipTypes(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }

    override suspend fun getOwnershipType(id: String): OwnershipType? =
        localCachedOwnershipTypes.find { it.id == id } ?: firestoreOwnershipTypes.whereEqualTo(
            FieldPath.documentId(), id
        ).limit(1).get(if (connection.available() != true) Source.CACHE else Source.DEFAULT).await()
            .firstOrNull()?.let { result ->
                val item = result.toObject<FirestoreCommonEntry>()
                val title = item.title ?: return@let null
                val type = OwnershipType(title, id = result.id)
                localCachedOwnershipTypes.add(type)
                type
            }

}
