package com.kophe.leskladlib.repository.deliverynote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldPath.documentId
import com.google.firebase.firestore.Source.CACHE
import com.google.firebase.firestore.Source.DEFAULT
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.connectivity.ConnectionStateMonitor
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonEntry

import com.kophe.leskladlib.datasource.firestore.FirestoreCommonInfoItem
import com.kophe.leskladlib.datasource.firestore.FirestoreDeliveryNote
import com.kophe.leskladlib.datasource.firestore.FirestoreLocation
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.CommonItem
import com.kophe.leskladlib.repository.common.DeliveryNote
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.Sublocation
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
class DefaultDeliveryNoteRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val locationsRepository: LocationsRepository,
    //internal val itemsRepository: ItemsRepository,
    private val unitsRepository: UnitsRepository?,
    private val userProfileRepository: UserProfileRepository,
    private val connection: ConnectionStateMonitor
) : DeliveryNoteRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreDeliveryNote by lazy { db.collection(builder.deliverynoteCollection) }
    internal val itemsCollection by lazy { db.collection(builder.itemsCollection) }
    private val localCachedDeliveryNote = mutableSetOf<DeliveryNote>()

    override suspend fun precacheValues() {
        if (localCachedDeliveryNote.isEmpty()) allDeliveryNotes(true)
    }

    override suspend fun allDeliveryNotes(forceReload: Boolean): TaskResult<List<DeliveryNote>, LSError> =
        try {
            log("allallDeliveryNotes(...) forceReload: $forceReload")
            if (!forceReload && localCachedDeliveryNote.isNotEmpty()) {
                TaskSuccess(localCachedDeliveryNote.sortedBy { it.deliveryNoteNumber }.toList())
            } else {
                localCachedDeliveryNote.clear()
                TaskSuccess(firestoreDeliveryNote.get(if (connection.available() != true) CACHE else DEFAULT)
                    .await().mapNotNull { result ->
                        log("${result.id} => ${result.data}")
                        val item = result.toObject<FirestoreDeliveryNote>()
                        log("deliverynote: $item")
                        val location =
                            parseFirestoreDeliveryNote(item, result.id) ?: return@mapNotNull null
                        localCachedDeliveryNote.add(location)
                        location
                    })
            }
        } catch (e: Exception) {
            log("allDeliveryNotes(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }
    private suspend fun parseFirestoreDeliveryNote(item: FirestoreDeliveryNote, id: String): DeliveryNote? {
        log("parseFirestoreDeliveryNote(...) forceReload: $id")
        val deliveryNote = item.delivery_note_number?.let {
            DeliveryNote(
                id = item.delivery_note_number,
                deliveryNoteNumber = item.delivery_note_number,
                deliveryNoteDate = item.delivery_note_date,
                deliveryNotePIB = item.delivery_note_PIB,
                from = item.from ?: "",
                to = "${item.to} ${findSublocation(item.to_sublocation_id)?.title ?: ""}",
                date = item.date_timestamp?.seconds?.times(1000)
                    ?.timestampToFormattedDate24h() ?: item.date ?: "",
                items = deliverynoteItems(item),
                notes = item.notes ?: "",
                responsibleUnit = unitsRepository?.getUnit(item.responsible_unit_id),
                receiverCallSign = item.receiver_call_sign
            )
        } ?: return null
        localCachedDeliveryNote.add(deliveryNote)
        return deliveryNote
    }

    override suspend fun getDeliveryNotebyID(id: String): DeliveryNote? =
        localCachedDeliveryNote.find { it.id == id } ?: firestoreDeliveryNote.whereEqualTo(
            FieldPath.documentId(), id
        ).limit(1).get(if (connection.available() != true) CACHE else DEFAULT).await().firstOrNull()
            ?.let { result ->
                val item = result.toObject<FirestoreDeliveryNote>()
                val deliveryNote = parseFirestoreDeliveryNote(item, result.id) ?: return@let null
                localCachedDeliveryNote.add(deliveryNote)
                return@let deliveryNote
            }

    override suspend fun getDeliveryNotebyNumber(id: String): DeliveryNote? =
        localCachedDeliveryNote.find { it.id == id } ?: firestoreDeliveryNote.whereEqualTo(
            FieldPath.documentId(), id
        ).limit(1).get(if (connection.available() != true) CACHE else DEFAULT).await().firstOrNull()
            ?.let { result ->
                val item = result.toObject<FirestoreDeliveryNote>()
                val deliveryNote = parseFirestoreDeliveryNote(item, result.id) ?: return@let null
                localCachedDeliveryNote.add(deliveryNote)
                return@let deliveryNote
            }

    //TODO: move to Delivery Note constructor
    override suspend fun findDeliveryNoteById(id: String?): TaskResult<DeliveryNote, LSError> {
        log("findDeliveryNoteById(...), id=$id")
        if (id.isNullOrEmpty()) return TaskError<DeliveryNote, LSError>(SimpleError("couldn't find Delivery Note $id"))
        try {
            return TaskSuccess(
                firestoreDeliveryNote.whereEqualTo(documentId(), id).get().await()
                    .mapNotNull { document ->
                        val item = document.toObject<FirestoreDeliveryNote>()
                        if (item.from.isNullOrEmpty() || item.to.isNullOrEmpty() || item.date.isNullOrEmpty()) return@mapNotNull null
                        DeliveryNote(
                            id = item.delivery_note_number,
                            deliveryNoteNumber = item.delivery_note_number,
                            deliveryNoteDate = item.delivery_note_date,
                            deliveryNotePIB = item.delivery_note_PIB,
                            from = item.from,
                            to = "${item.to} ${findSublocation(item.to_sublocation_id)?.title ?: ""}",
                            date = item.date_timestamp?.seconds?.times(1000)
                                ?.timestampToFormattedDate24h() ?: item.date,
                            items = deliverynoteItems(item),
                            notes = item.notes ?: "",
                            responsibleUnit = unitsRepository?.getUnit(item.responsible_unit_id),
                            receiverCallSign = item.receiver_call_sign
                        )
                    }.firstOrNull()
                    ?: return TaskError<DeliveryNote, LSError>(SimpleError("couldn't find delivery note $id"))
            )
        } catch (e: Exception) {
            log("findDeliveryNoteById(...) failed due to ${e.message}")
            return TaskError<DeliveryNote, LSError>(SimpleError("couldn't find delivery note $id"))
        }
    }

    private suspend fun findSublocation(id: String?) =
        if (!id.isNullOrEmpty()) locationsRepository.getSublocation(id) else null

    override suspend fun deliverynoteList(forceRefresh: Boolean): TaskResult<List<DeliveryNote>, LSError> =
        try {
            log("deliverynoteList force refresh $forceRefresh")
            if (!forceRefresh && localCachedDeliveryNote.isNotEmpty()) TaskSuccess(localCachedDeliveryNote.toList())
            else {
                localCachedDeliveryNote.clear()
                TaskSuccess(firestoreDeliveryNote.get().await().mapNotNull { document ->
                    val item = document.toObject<FirestoreDeliveryNote>()
                    if (item.from.isNullOrEmpty() || item.to.isNullOrEmpty() || item.date.isNullOrEmpty()) return@mapNotNull null
                    val deliverynote = DeliveryNote(
                        id = item.delivery_note_number,
                        deliveryNoteNumber = item.delivery_note_number,
                        deliveryNoteDate = item.delivery_note_date,
                        deliveryNotePIB = item.delivery_note_PIB,
                        from = item.from,
                        to = "${item.to} ${findSublocation(item.to_sublocation_id)?.title ?: ""}",
                        date = item.date_timestamp?.seconds?.times(1000)
                            ?.timestampToFormattedDate24h() ?: item.date,
                        items = deliverynoteItems(item),
                        notes = item.notes ?: "",
                        responsibleUnit = unitsRepository?.getUnit(item.responsible_unit_id),
                        receiverCallSign = item.receiver_call_sign
                    )
                    localCachedDeliveryNote.add(deliverynote)
                    deliverynote
                })
            }
        } catch (e: Exception) {
            log("deliverynoteList() failed due to: ${e.message}")
            TaskError(SimpleError(e.message))
        }

    private fun deliverynoteItems(item: FirestoreDeliveryNote) = item.deliverynote_items?.mapNotNull {
        CommonItem(
            title = it.title ?: return@mapNotNull null,
            firestoreId = it.firestore_id ?: return@mapNotNull null
        )
    } ?: emptyList()

    override suspend fun createDeliveryNote(
        items: List<Item>,
        deliverynoteInfoContainer: DeliveryNoteInfoContainer,
        forceDivideQuantityItems: Boolean
    ): TaskResult<*, LSError> {
        log("createDeliveryNote(...): deliverynoteInfoContainer=${deliverynoteInfoContainer}")
        if (deliverynoteInfoContainer.from.isEmpty()) return TaskError<Unit, LSError>(SimpleError("no user found"))
        val date = System.currentTimeMillis().timestampToFormattedDate24h()
        val dateTimestamp = Timestamp(Date())
        val writeBatch = db.batch()
        return try {

//            val quantityResult = dealWithQuantityItems(
//                deliverynoteInfoContainer = deliverynoteInfoContainer,
//                quantityItems = items.filter { it.quantity != null },
//                writeBatch = writeBatch,
//                forceDivideQuantityItems
//            )
//            items.filter { it.quantity == null }.forEach { item ->
//                item.location = Location(
//                    deliverynoteInfoContainer.location.title,
//                    deliverynoteInfoContainer.location.id,
//                    emptyList()
//                )
//                item.sublocation = deliverynoteInfoContainer.sublocation
//                moveItem(writeBatch, item.firestoreId!!, deliverynoteInfoContainer)
//            }
            val commonItems: MutableList<FirestoreCommonInfoItem> = mutableListOf<FirestoreCommonInfoItem>()
            //val commonItems = List<FirestoreCommonInfoItem>//quantityResult.toMutableList()
            commonItems.addAll(items.filter { it.quantity == null }.map {
                FirestoreCommonInfoItem(
                    title = it.titleString(), firestore_id = it.firestoreId
                )
            })

            val key = key(
                date = Date(),
                "${deliverynoteInfoContainer.from} ${deliverynoteInfoContainer.location.title}"
            )
            writeBatch.set(
                firestoreDeliveryNote.document(key), FirestoreDeliveryNote(

                    delivery_note_number = deliverynoteInfoContainer.deliveryNoteNumber,
                    delivery_note_date = deliverynoteInfoContainer.deliveryNoteDate,
                    delivery_note_PIB = deliverynoteInfoContainer.deliveryNotePIB,
                    from = deliverynoteInfoContainer.from,
                    to = deliverynoteInfoContainer.location.title,
                    date = date,
                    date_timestamp = dateTimestamp,
                    deliverynote_items = commonItems,
                    to_location_id = deliverynoteInfoContainer.location.id,
                    notes = deliverynoteInfoContainer.notes.validated(),
                    to_sublocation_id = deliverynoteInfoContainer.sublocation?.id,
                    responsible_unit_id = deliverynoteInfoContainer.responsibleUnit?.id,
                    receiver_call_sign = deliverynoteInfoContainer.receiver,
                    user_email = userProfileRepository.user()?.email
                )
            )
            writeBatch.commit().await()
            TaskSuccess<Any, LSError>()
        } catch (e: Exception) {
            log("createDeliveryNote(...) failed due to: ${e.message}")
            TaskError<Any, LSError>(SimpleError("${e.message}"))
        }
    }

    internal fun moveItem(
        writeBatch: WriteBatch, firestoreId: String, deliverynoteInfoContainer: DeliveryNoteInfoContainer
    ) {
        writeBatch.update(
            itemsCollection.document(firestoreId),
            "location_id",
            deliverynoteInfoContainer.location.id,
            "sublocation_id",
            deliverynoteInfoContainer.sublocation?.id,
            "responsible_unit_id",
            deliverynoteInfoContainer.responsibleUnit?.id
        )
    }

}
