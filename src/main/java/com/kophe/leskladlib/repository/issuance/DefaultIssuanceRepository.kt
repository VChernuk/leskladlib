package com.kophe.leskladlib.repository.issuance

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath.documentId
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonInfoItem
import com.kophe.leskladlib.datasource.firestore.FirestoreIssuance
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.CommonItem
import com.kophe.leskladlib.repository.common.Issuance
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import com.kophe.leskladlib.repository.items.ItemsRepository
import com.kophe.leskladlib.repository.locations.LocationsRepository
import com.kophe.leskladlib.repository.units.UnitsRepository
import com.kophe.leskladlib.repository.userprofile.UserProfileRepository
import com.kophe.leskladlib.timestampToFormattedDate24h
import com.kophe.leskladlib.validated
import kotlinx.coroutines.tasks.await
import java.util.Date

//TODO: move items' manipulations to items repository
class DefaultIssuanceRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val locationsRepository: LocationsRepository,
    internal val itemsRepository: ItemsRepository,
    private val unitsRepository: UnitsRepository?,
    private val userProfileRepository: UserProfileRepository
) : IssuanceRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreIssuance by lazy { db.collection(builder.issuanceCollection) }
    internal val itemsCollection by lazy { db.collection(builder.itemsCollection) }
    private val localCachedIssuance = mutableSetOf<Issuance>()


    //TODO: move to issuance constructor
    override suspend fun findIssuanceById(id: String?): TaskResult<Issuance, LSError> {
        log("findIssuanceById(...), id=$id")
        if (id.isNullOrEmpty()) return TaskError<Issuance, LSError>(SimpleError("couldn't find issuance $id"))
        try {
            return TaskSuccess(
                firestoreIssuance.whereEqualTo(documentId(), id).get().await()
                    .mapNotNull { document ->
                        val item = document.toObject<FirestoreIssuance>()
                        if (item.from.isNullOrEmpty() || item.to.isNullOrEmpty() || item.date.isNullOrEmpty()) return@mapNotNull null
                        Issuance(
                            from = item.from,
                            to = "${item.to} ${findSublocation(item.to_sublocation_id)?.title ?: ""}",
                            date = item.date_timestamp?.seconds?.times(1000)
                                ?.timestampToFormattedDate24h() ?: item.date,
                            issuanceItems(item),
                            item.notes ?: "",
                            responsibleUnit = unitsRepository?.getUnit(item.responsible_unit_id),
                            receiverCallSign = item.receiver_call_sign
                        )
                    }.firstOrNull()
                    ?: return TaskError<Issuance, LSError>(SimpleError("couldn't find issuance $id"))
            )
        } catch (e: Exception) {
            log("findIssuanceById(...) failed due to ${e.message}")
            return TaskError<Issuance, LSError>(SimpleError("couldn't find issuance $id"))
        }
    }

    private suspend fun findSublocation(id: String?) =
        if (!id.isNullOrEmpty()) locationsRepository.getSublocation(id) else null

    override suspend fun issuanceList(forceRefresh: Boolean): TaskResult<List<Issuance>, LSError> =
        try {
            log("issuanceList force refresh $forceRefresh")
            if (!forceRefresh && localCachedIssuance.isNotEmpty()) TaskSuccess(localCachedIssuance.toList())
            else {
                localCachedIssuance.clear()
                TaskSuccess(firestoreIssuance.get().await().mapNotNull { document ->
                    val item = document.toObject<FirestoreIssuance>()
                    if (item.from.isNullOrEmpty() || item.to.isNullOrEmpty() || item.date.isNullOrEmpty()) return@mapNotNull null
                    val issuance = Issuance(
                        from = item.from,
                        to = "${item.to} ${findSublocation(item.to_sublocation_id)?.title ?: ""}",
                        date = item.date_timestamp?.seconds?.times(1000)
                            ?.timestampToFormattedDate24h() ?: item.date,
                        issuanceItems(item),
                        item.notes ?: "",
                        responsibleUnit = unitsRepository?.getUnit(item.responsible_unit_id),
                        receiverCallSign = item.receiver_call_sign
                    )
                    localCachedIssuance.add(issuance)
                    issuance
                })
            }
        } catch (e: Exception) {
            log("issuanceList() failed due to: ${e.message}")
            TaskError(SimpleError(e.message))
        }

    private fun issuanceItems(item: FirestoreIssuance) = item.issuance_items?.mapNotNull {
        CommonItem(
            title = it.title ?: return@mapNotNull null,
            firestoreId = it.firestore_id ?: return@mapNotNull null
        )
    } ?: emptyList()

    override suspend fun createIssuance(
        items: List<Item>,
        issuanceInfoContainer: IssuanceInfoContainer,
        forceDivideQuantityItems: Boolean
    ): TaskResult<*, LSError> {
        log("createIssuance(...): issuanceInfoContainer=${issuanceInfoContainer}")
        if (issuanceInfoContainer.from.isEmpty()) return TaskError<Unit, LSError>(SimpleError("no user found"))
        val date = System.currentTimeMillis().timestampToFormattedDate24h()
        val dateTimestamp = Timestamp(Date())
        val writeBatch = db.batch()
        return try {
            val quantityResult = dealWithQuantityItems(
                issuanceInfoContainer = issuanceInfoContainer,
                quantityItems = items.filter { it.quantity != null },
                writeBatch = writeBatch,
                forceDivideQuantityItems
            )
            items.filter { it.quantity == null }.forEach { item ->
                item.location = Location(
                    issuanceInfoContainer.location.title,
                    issuanceInfoContainer.location.id,
                    emptyList()
                )
                item.sublocation = issuanceInfoContainer.sublocation
                moveItem(writeBatch, item.firestoreId!!, issuanceInfoContainer)
            }
            val commonItems = quantityResult.toMutableList()
            commonItems.addAll(items.filter { it.quantity == null }.map {
                FirestoreCommonInfoItem(
                    title = it.titleString(), firestore_id = it.firestoreId
                )
            })
            val key = key(
                date = Date(),
                "${issuanceInfoContainer.from} ${issuanceInfoContainer.location.title}"
            )
            writeBatch.set(
                firestoreIssuance.document(key), FirestoreIssuance(
                    from = issuanceInfoContainer.from,
                    to = issuanceInfoContainer.location.title,
                    date = date,
                    date_timestamp = dateTimestamp,
                    issuance_items = commonItems,
                    to_location_id = issuanceInfoContainer.location.id,
                    notes = issuanceInfoContainer.notes.validated(),
                    to_sublocation_id = issuanceInfoContainer.sublocation?.id,
                    responsible_unit_id = issuanceInfoContainer.responsibleUnit?.id,
                    receiver_call_sign = issuanceInfoContainer.receiver,
                    user_email = userProfileRepository.user()?.email
                )
            )
            writeBatch.commit().await()
            itemsRepository.setupItemsHistory(
                issuanceInfoContainer.from,
                issuanceInfoContainer.location.title,
                date,
                items,
                key,
                issuanceInfoContainer.receiver
            )
            TaskSuccess<Any, LSError>()
        } catch (e: Exception) {
            log("createIssuance(...) failed due to: ${e.message}")
            TaskError<Any, LSError>(SimpleError("${e.message}"))
        }
    }

    internal fun moveItem(
        writeBatch: WriteBatch, firestoreId: String, issuanceInfoContainer: IssuanceInfoContainer
    ) {
        writeBatch.update(
            itemsCollection.document(firestoreId),
            "location_id",
            issuanceInfoContainer.location.id,
            "sublocation_id",
            issuanceInfoContainer.sublocation?.id,
            "responsible_unit_id",
            issuanceInfoContainer.responsibleUnit?.id
        )
    }

}
