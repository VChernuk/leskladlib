package com.kophe.leskladlib.repository.deliverynote

import com.kophe.leskladlib.repository.common.DeliveryNote
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.ResponsibleUnit
import com.kophe.leskladlib.repository.common.Sublocation
import com.kophe.leskladlib.repository.common.TaskResult

interface DeliveryNoteRepository {

    suspend fun precacheValues()

    suspend fun findDeliveryNoteById(id: String?): TaskResult<DeliveryNote, LSError>

    suspend fun deliverynoteList(forceRefresh: Boolean = true): TaskResult<List<DeliveryNote>, LSError>

//    suspend fun allDeliveryNotes(): TaskResult<List<DeliveryNote>, LSError>
    suspend fun allDeliveryNotes(forceReload: Boolean): TaskResult<List<DeliveryNote>, LSError>
    suspend fun getDeliveryNotebyID(id: String): DeliveryNote?
//    suspend fun getDeliveryNote(id: String): DeliveryNote?
    suspend fun getDeliveryNotebyNumber(number: String): DeliveryNote?

    suspend fun createDeliveryNote(
        items: List<Item>,
        deliverynoteInfoContainer: DeliveryNoteInfoContainer,
        forceDivideQuantityItems: Boolean = false
    ): TaskResult<*, LSError>

}

data class DeliveryNoteInfoContainer(
    val deliveryNoteNumber: String?,
    val deliveryNoteDate: String?,
    val deliveryNotePIB: String?,
    val from: String,
    val receiver: String,
    val location: Location,
    val sublocation: Sublocation?,
    val responsibleUnit: ResponsibleUnit?,
    val notes: String
)
