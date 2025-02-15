package com.kophe.leskladlib.repository.deliverynotes

import com.google.firebase.firestore.FirebaseFirestore
import com.kophe.leskladlib.repository.common.DeliveryNote
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.loggingTag
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DeliveryNotesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val loggingUtil: LoggingUtil
) {

    suspend fun getDeliveryNotes(): List<DeliveryNote> {
        return try {
            loggingUtil.log("${loggingTag()} Fetching delivery notes from Firestore...")
            val snapshot = firestore.collection("deliverynotes").get().await()
            val notes = snapshot.toObjects(DeliveryNote::class.java)
            loggingUtil.log("${loggingTag()} Loaded ${notes.size} delivery notes from Firestore")
            notes
        } catch (e: Exception) {
            loggingUtil.log("${loggingTag()} Firestore error: ${e.message}")
            emptyList()
        }
    }
}
