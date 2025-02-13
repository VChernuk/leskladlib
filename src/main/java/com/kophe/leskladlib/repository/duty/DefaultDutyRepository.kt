package com.kophe.leskladlib.repository.duty

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.datasource.firestore.FirestoreDuty
import com.kophe.leskladlib.datasource.firestore.FirestoreDutyItem
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.*
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import com.kophe.leskladlib.repository.locations.LocationsRepository
import com.kophe.leskladlib.timestampToFormattedDate24h
import kotlinx.coroutines.tasks.await
import java.util.*

//TODO: manually choose firestore id
//TODO: move items' manipulations to items repository
class DefaultDutyRepository(
    loggingUtil: LoggingUtil, builder: RepositoryBuilder,
    private val locationsRepository: LocationsRepository,
) : DutyRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firebaseDuty by lazy { db.collection(builder.dutyCollection) }

    override suspend fun dutyList(): TaskResult<List<Duty>, LSError> = try {
        log("dutyList()")
        TaskSuccess(firebaseDuty.orderBy(
            "date_created", Query.Direction.DESCENDING
        ).get().await().mapNotNull { document ->
            val item = document.toObject<FirestoreDuty>()
            if (item.user.isNullOrEmpty() || item.sublocation_id.isNullOrEmpty() || item.date_created == null) return@mapNotNull null
            Duty(
                user = item.user,
                sublocation = locationsRepository.getSublocation(item.sublocation_id)
                    ?: return@mapNotNull null,
                approvedItems = item.approved_items?.mapNotNull approved@{ approvedItem ->
                    DutyItem(
                        title = approvedItem.title,
                        id = approvedItem.id,
                        barcode = approvedItem.barcode,
                        firestoreId = approvedItem.firestore_id ?: return@approved null
                    )
                } ?: emptyList(),
                declinedItems = item.declined_items?.mapNotNull declined@{ declinedItem ->
                    DutyItem(
                        title = declinedItem.title,
                        id = declinedItem.id,
                        barcode = declinedItem.barcode,
                        firestoreId = declinedItem.firestore_id ?: return@declined null
                    )
                } ?: emptyList(),
                date = item.date_created.seconds.times(1000).timestampToFormattedDate24h(),
            )
        })
    } catch (e: Exception) {
        log("issuanceList() failed due to: ${e.message}")
        TaskError(LSError.SimpleError(e.message))
    }

    override suspend fun createDuty(
        user: String, sublocation: Sublocation, approvedItems: List<Item>, declinedItems: List<Item>
    ): TaskResult<*, LSError> {
        try {
            val now = Date()
            return TaskSuccess(
                firebaseDuty.document().set(
                    FirestoreDuty(
                        user = user,
                        sublocation_id = sublocation.id,
                        sublocation_name = sublocation.title,
                        approved_items = approvedItems.map {
                            FirestoreDutyItem(
                                title = it.title,
                                id = it.id,
                                firestore_id = it.firestoreId,
                                barcode = it.barcode
                            )
                        },
                        declined_items = declinedItems.map {
                            FirestoreDutyItem(
                                title = it.title,
                                id = it.id,
                                firestore_id = it.firestoreId,
                                barcode = it.barcode
                            )
                        },
                        date_created = Timestamp(now)
                    )
                ).await()
            )
        } catch (e: Exception) {
            log("createItem(...) failed due to: ${e.message}")
            return TaskError<Any, LSError>(LSError.SimpleError("${e.message}"))
        }
    }
}
