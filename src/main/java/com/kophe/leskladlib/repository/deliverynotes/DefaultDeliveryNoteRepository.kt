package com.kophe.leskladlib.repository.deliverynotes

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
//import com.google.firebase.firestore.toObject
import com.kophe.leskladlib.datasource.firestore.FirestoreDeliveryNote
import com.kophe.leskladlib.repository.common.DeliveryNote
import com.kophe.leskladlib.repository.locations.LocationsRepository
import com.kophe.leskladlib.repository.items.ItemsRepository
import com.kophe.leskladlib.repository.userprofile.UserProfileRepository
//import com.kophe.leskladlib.util.ConnectionStateMonitor
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.units.UnitsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath.documentId
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonInfoItem
import com.kophe.leskladlib.datasource.firestore.FirestoreIssuance
//import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.CommonItem
import com.kophe.leskladlib.repository.common.Issuance
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.Location
//import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
//import com.kophe.leskladlib.repository.items.ItemsRepository
//import com.kophe.leskladlib.repository.locations.LocationsRepository
//import com.kophe.leskladlib.repository.units.UnitsRepository
//import com.kophe.leskladlib.repository.userprofile.UserProfileRepository
import com.kophe.leskladlib.timestampToFormattedDate24h
import com.kophe.leskladlib.validated
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class DefaultDeliveryNoteRepository @Inject constructor(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val locationsRepository: LocationsRepository,
    internal val itemsRepository: ItemsRepository,
    private val unitsRepository: UnitsRepository?,
    private val userProfileRepository: UserProfileRepository
) : DeliveryNotesRepository, BaseRepository(loggingUtil)  {

    private val db by lazy { Firebase.firestore }
    private val deliveryNotesCollection by lazy { db.collection(builder.deliveryNotesCollection) }
    internal val itemsCollection by lazy { db.collection(builder.itemsCollection) }
    private val localCachedIssuance = mutableSetOf<Issuance>()

//    private val firestore = FirebaseFirestore.getInstance()
//    private val collection = firestore.collection("delivery_notes")

    override  fun getDeliveryNotesFlow(): Flow<List<DeliveryNote>> = callbackFlow {
        val listener = deliveryNotesCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    log("Firestore error: ${error.message}")
                    return@addSnapshotListener
                }
                launch { // Запускаем suspend-код в coroutineScope
                    val deliveryNotes = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject<FirestoreDeliveryNote>()?.toDomainModel()
                    } ?: emptyList()

                    trySend(deliveryNotes)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getDeliveryNotes(): List<DeliveryNote> {
        return try {
            val snapshot = deliveryNotesCollection.get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject<FirestoreDeliveryNote>()?.toDomainModel()
            }
        } catch (e: Exception) {
            log("Error getting delivery notes: ${e.message}")
            emptyList()
        }
    }

    override suspend fun addDeliveryNote(deliveryNote: DeliveryNote) {
        try {
            deliveryNotesCollection.document(deliveryNote.number).set(deliveryNote.toFirestoreModel()).await()
        } catch (e: Exception) {
            log("Error adding delivery note: ${e.message}")
        }
    }

    override suspend fun updateDeliveryNote(deliveryNote: DeliveryNote) {
        try {
            deliveryNotesCollection.document(deliveryNote.number).set(deliveryNote.toFirestoreModel()).await()
        } catch (e: Exception) {
            log("Error updating delivery note: ${e.message}")
        }
    }

    override suspend fun deleteDeliveryNote(deliveryNoteNumber: String) {
        try {
            deliveryNotesCollection.document(deliveryNoteNumber).delete().await()
        } catch (e: Exception) {
            log("Error deleting delivery note: ${e.message}")
        }
    }
    private fun deliveryNoteItems(item: FirestoreDeliveryNote) = item.dn_items?.mapNotNull {
        CommonItem(
            title = it.title ?: return@mapNotNull null,
            firestoreId = it.firestore_id ?: return@mapNotNull null
        )
    } ?: emptyList()

    private suspend fun FirestoreDeliveryNote.toDomainModel(): DeliveryNote {

        return DeliveryNote(
            number = this.number ?: "",
            date = this.date ?: 0,
            location = locationsRepository.getLocation(this.to_location_id  ?: "") ?: Location.EMPTY,
            sublocation = this.to_sublocation_id?.let { locationsRepository.getSublocation(it) },
            responsiblePerson = this.responsible_person?: "",
            items = deliveryNoteItems(this)
        )
    }

    private fun DeliveryNote.toFirestoreModel(): FirestoreDeliveryNote {
        return FirestoreDeliveryNote(
            number = this.number,
            date = this.date,
            to_location_id = this.location.id,
            to_sublocation_id = this.sublocation?.id ?: "",
            responsible_person = this.responsiblePerson,
            dn_items = this.items.map { it.toFirestoreModel() }
        )
    }

}
