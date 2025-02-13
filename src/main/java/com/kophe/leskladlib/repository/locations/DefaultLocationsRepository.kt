package com.kophe.leskladlib.repository.locations

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source.CACHE
import com.google.firebase.firestore.Source.DEFAULT
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.connectivity.ConnectionStateMonitor
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonEntry
import com.kophe.leskladlib.datasource.firestore.FirestoreLocation
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.replaceUnsupportedChars
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.Sublocation
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import com.kophe.leskladlib.transliterate
import com.kophe.leskladlib.validated
import kotlinx.coroutines.tasks.await

class DefaultLocationsRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val connection: ConnectionStateMonitor
) : LocationsRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreLocations by lazy { db.collection(builder.locationsCollection) }
    private val firestoreSublocations by lazy { db.collection(builder.sublocations) }
    private val localCachedLocations = mutableSetOf<Location>()
    private val localCachedSublocations = mutableSetOf<Sublocation>()

    override suspend fun precacheValues() {
        if (localCachedSublocations.isEmpty()) allSublocations()
        if (localCachedLocations.isEmpty()) allLocations()
    }

    override suspend fun allSublocations(): TaskResult<List<Sublocation>, LSError> {
        log("allSublocations(...)")
        try {
            val connectionAvailable = connection.available()
            log("allSublocations connection.available: $connectionAvailable")
            return TaskSuccess(firestoreSublocations.get(if (connection.available() != true) CACHE else DEFAULT)
                .await().mapNotNull { result ->
                    log("${result.id} => ${result.data}")
                    val item = result.toObject<FirestoreCommonEntry>()
                    log("sublocation: $item")
                    val sublocation =
                        parseFirestoreSublocation(item, result.id) ?: return@mapNotNull null
                    localCachedSublocations.add(sublocation)
                    sublocation
                })
        } catch (e: Exception) {
            log("allSublocations(...) failed due to: ${e.message}")
            return TaskError(SimpleError("${e.message}"))
        }
    }

    override suspend fun getSublocation(id: String): Sublocation? =
        localCachedSublocations.find { it.id == id } ?: firestoreSublocations.whereEqualTo(
            FieldPath.documentId(), id
        ).limit(1).get(if (connection.available() != true) CACHE else DEFAULT).await().firstOrNull()
            ?.let { result ->
                val item = result.toObject<FirestoreCommonEntry>()
                val sublocation = parseFirestoreSublocation(item, result.id) ?: return@let null
                localCachedSublocations.add(sublocation)
                return@let sublocation
            }

    private fun parseFirestoreSublocation(item: FirestoreCommonEntry, id: String): Sublocation? =
        item.title?.let { Sublocation(it, id = id) }

    override suspend fun allLocations(forceReload: Boolean): TaskResult<List<Location>, LSError> =
        try {
            log("allLocations(...) forceReload: $forceReload")
            if (!forceReload && localCachedLocations.isNotEmpty()) {
                TaskSuccess(localCachedLocations.sortedBy { it.title }.toList())
            } else {
                localCachedSublocations.clear()
                localCachedLocations.clear()
                allSublocations()
                TaskSuccess(firestoreLocations.get(if (connection.available() != true) CACHE else DEFAULT)
                    .await().mapNotNull { result ->
                        log("${result.id} => ${result.data}")
                        val item = result.toObject<FirestoreLocation>()
                        log("location: $item")
                        val location =
                            parseFirestoreLocation(item, result.id) ?: return@mapNotNull null
                        localCachedLocations.add(location)
                        location
                    })
            }
        } catch (e: Exception) {
            log("allLocations(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }

    override suspend fun getLocation(id: String): Location? =
        localCachedLocations.find { it.id == id } ?: firestoreLocations.whereEqualTo(
            FieldPath.documentId(), id
        ).limit(1).get(if (connection.available() != true) CACHE else DEFAULT).await().firstOrNull()
            ?.let { result ->
                val item = result.toObject<FirestoreLocation>()
                val location = parseFirestoreLocation(item, result.id) ?: return@let null
                localCachedLocations.add(location)
                return@let location
            }

    private suspend fun parseFirestoreLocation(item: FirestoreLocation, id: String): Location? {
        val location = item.title?.let {
            Location(it,
                id = id,
                item.sublocations?.mapNotNull { subId -> getSublocation(subId) } ?: emptyList())
        } ?: return null
        localCachedLocations.add(location)
        return location
    }

    override suspend fun updateLocation(
        location: Location
    ): TaskResult<Any, LSError> {
        log("createLocation(...): title=${location.title}")
        val title = location.title
        val batchWriteBatch = db.batch()
        return try {
            val sublocationKeys = mutableSetOf<String>()
            location.sublocations.forEach {
                val sublocationKey = it.id.ifEmpty {
                    (title + "_" + it.title).validated().transliterate()
                        .replaceUnsupportedChars() + "_" + title.hashCode()
                }
                batchWriteBatch.set(
                    firestoreSublocations.document(sublocationKey), FirestoreCommonEntry(it.title)
                )
                sublocationKeys.add(sublocationKey)
            }
            val key = location.id.ifEmpty {
                title.validated().transliterate().replaceUnsupportedChars()
            }
            batchWriteBatch.set(
                firestoreLocations.document(key), FirestoreLocation(title, sublocationKeys.toList())
            )
            TaskSuccess(batchWriteBatch.commit().await())
        } catch (e: Exception) {
            log("createItem(...) failed due to: ${e.message}")
            TaskError<Any, LSError>(SimpleError("${e.message}"))
        }
    }
}
