package com.kophe.leskladlib.repository.units

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source.CACHE
import com.google.firebase.firestore.Source.DEFAULT
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.connectivity.ConnectionStateMonitor
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonEntry
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.ResponsibleUnit
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import kotlinx.coroutines.tasks.await

class DefaultUnitsRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val connection: ConnectionStateMonitor
) : UnitsRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreUnits by lazy { db.collection(builder.unitsCollection) }
    private val localCachedUnits = mutableSetOf<ResponsibleUnit>()

    override suspend fun precacheValues() {
        if (localCachedUnits.isEmpty()) allUnits()
    }

    override suspend fun allUnits(forceReload: Boolean): TaskResult<List<ResponsibleUnit>, LSError> {
        try {
            return TaskSuccess(firestoreUnits.get(if (connection.available() != true) CACHE else DEFAULT)
                .await().mapNotNull { result ->
                    log("${result.id} => ${result.data}")
                    val item = result.toObject<FirestoreCommonEntry>()
                    log("location: $item")
                    val responsibleUnit =
                        parseFirestoreUnit(item, result.id) ?: return@mapNotNull null
                    localCachedUnits.add(responsibleUnit)
                    responsibleUnit
                })
        } catch (e: Exception) {
            log("allSublocations(...) failed due to: ${e.message}")
            return TaskError(SimpleError("${e.message}"))
        }
    }

    override suspend fun getUnit(id: String?): ResponsibleUnit? =
        if (id.isNullOrEmpty()) null else localCachedUnits.find { it.id == id }
            ?: firestoreUnits.whereEqualTo(
                FieldPath.documentId(), id
            ).limit(1).get(if (connection.available() != true) CACHE else DEFAULT).await()
                .firstOrNull()?.let { result ->
                    val item = result.toObject<FirestoreCommonEntry>()
                    val responsibleUnit = parseFirestoreUnit(item, result.id) ?: return null
                    localCachedUnits.add(responsibleUnit)
                    responsibleUnit
                }

    private fun parseFirestoreUnit(item: FirestoreCommonEntry, id: String): ResponsibleUnit? =
        item.title?.let { ResponsibleUnit(it, id = id) }


}
