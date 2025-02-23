package com.kophe.leskladlib.datasource.firestore

import com.google.firebase.Timestamp
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.validated
import com.kophe.leskladlib.repository.common.CommonItem
import com.kophe.leskladlib.repository.common.DeliveryNote
import com.kophe.leskladlib.repository.common.Location
import java.util.Date

data class FirestoreCommonInfoItem(
    val title: String? = null, val firestore_id: String? = null
){
    fun toDomainModel(): CommonItem {
        return CommonItem(
            title = this.title,
            firestoreId = this.firestore_id
        )
    }
}

internal data class FirestoreItemImage(
    val path: String? = null, val date: String? = null
)

internal data class FirestoreItem(
    val title: String? = null,
    val barcode: String? = null,
    val id: String? = null,
    val location_id: String? = null,
    val sublocation_id: String? = null,
    val category_id: String? = null,
    val notes: String? = null,
    val subcategory_ids: List<String>? = null,
    val date_created: com.google.firebase.Timestamp? = null,
    val date_modified: com.google.firebase.Timestamp? = null,
    val history: List<FirestoreCommonInfoItem>? = null,
    val ownership_type_id: String? = null,
    val item_images: List<FirestoreItemImage>? = null,
    val responsible_unit_id: String? = null,
    val serial_number: String? = null,
    val quantity: FirestoreQuantity? = null,
    val set_options: FirestoreSetOptions? = null
    , val delivery_note_id: String? = null
) {
    constructor(item: Item, now: Date) : this(barcode = item.barcode?.validated(),
        title = item.title?.validated(),
        location_id = item.location?.id,
        category_id = item.category?.id,
        id = item.id?.validated(),
        notes = item.notes?.validated(),
        subcategory_ids = item.subcategories.map { it.id },
        date_created = Timestamp(now),
        date_modified = Timestamp(now),
        history = emptyList(),//TODO: review this
        item_images = item.images?.map { FirestoreItemImage(it.url, it.date) },
        ownership_type_id = item.ownershipType?.id,
        sublocation_id = item.sublocation?.id,
        responsible_unit_id = item.responsibleUnit?.id,
        serial_number = item.sn?.validated(),
        quantity = item.quantity?.let {
            if (it.quantity > 0) FirestoreQuantity(
                it.parentId, it.quantity, it.measurement.validated()
            ) else null
        },
        set_options = item.setOptions?.let { iso ->
            FirestoreSetOptions(
                iso.parentItem?.firestoreId, iso.subItems.mapNotNull { it.firestoreId }.toList()
            )
        })
}

internal data class FirestoreSetOptions(
    val parent_set_id: String? = null, val subItemIds: List<String>? = null
)

internal data class FirestoreQuantity(
    val parent_id: String? = null,
    val quantity: Int? = null,
    val measurement: String? = null//, val uniqueId: String? = null
)

internal data class FirestoreCommonEntry(val title: String? = null)

internal data class FirestoreLocation(
    val title: String? = null, val sublocations: List<String>? = emptyList()
)

internal data class FirestoreCategory(
    val title: String? = null,
    val subcategories: List<String>? = emptyList(),
    val category_weight: Int? = null
)

internal data class FirestoreSubcategory(
    val title: String? = null, val subcategory_weight: Int? = null
)

internal data class FirestoreIssuance(
    val from: String? = null,
    val to: String? = null,
    val date: String? = null,
    val date_timestamp: com.google.firebase.Timestamp? = null,
    val issuance_items: List<FirestoreCommonInfoItem>? = null,
    val to_location_id: String? = null,
    val notes: String? = null,
    val to_sublocation_id: String? = null,
    val responsible_unit_id: String? = null,
    val receiver_call_sign: String? = null,
    val user_email: String? = null
)

internal data class FirestoreDeliveryNote(
    val number: String,
    val date: Timestamp = Timestamp.now(), // Изменяем Long -> Timestamp,
    val to_location_id: String,
    val to_sublocation_id: String? = null,
    val responsible_person: String,
    val dn_items: List<FirestoreCommonInfoItem>? = emptyList()
){
    constructor() : this("", Timestamp.now(), "", "", "", emptyList()) // Обязательный пустой конструктор

    private fun FirestoreDeliveryNote.toDomainModel(): DeliveryNote {
        return DeliveryNote(
            number = this.number,
            date = this.date.toDate(),
            location = Location.EMPTY,
            sublocation = null,
            responsiblePerson = this.responsible_person,
            items = this.dn_items!!.map { it.toDomainModel() } // Конвертируем `FirestoreCommonInfoItem` в `CommonItem`
        )
    }
}
//@Keep
//data class FirestoreDeliveryNote(
//    @get:PropertyName("number") @set:PropertyName("number")
//    var number: String = "",
//
//    @get:PropertyName("date") @set:PropertyName("date")
//    var date: Long = System.currentTimeMillis(),
//
//    @get:PropertyName("to_location_id") @set:PropertyName("to_location_id")
//    var toLocationId: String = "",
//
//    @get:PropertyName("to_sublocation_id") @set:PropertyName("to_sublocation_id")
//    var toSublocationId: String = "",
//
//    @get:PropertyName("responsible_person") @set:PropertyName("responsible_person")
//    var responsiblePerson: String = "",
//
//    @get:PropertyName("dn_items") @set:PropertyName("dn_items")
//    var dnItems: List<FirestoreCommonInfoItem> = emptyList()
//)

internal data class FirestoreDuty(
    val user: String? = null,
    val sublocation_name: String? = null,
    val sublocation_id: String? = null,
    val approved_items: List<FirestoreDutyItem>? = null,
    val declined_items: List<FirestoreDutyItem>? = null,
    val date_created: com.google.firebase.Timestamp? = null,
)

internal data class FirestoreDutyItem(
    val title: String? = null,
    val id: String? = null,
    val barcode: String? = null,
    val firestore_id: String? = null
)

internal data class FirestoreBackupObject(
    val issuance: List<Pair<String, FirestoreIssuance>>,
    val locations: List<Pair<String, FirestoreLocation>>,
    val sublocations: List<Pair<String, FirestoreCommonEntry>>,
    val categories: List<Pair<String, FirestoreCategory>>,
    val subcategories: List<Pair<String, FirestoreSubcategory>>,
    val items: List<Pair<String, FirestoreItem>>,
    val ownershipTypes: List<Pair<String, FirestoreCommonEntry>>,
    val users: List<Pair<String, FirestoreCommonEntry>>,
    val duty: List<Pair<String, FirestoreDuty>>?,
    val responsibleUnits: List<Pair<String, FirestoreCommonEntry>>?,
    val adminUsers: List<Pair<String, FirestoreCommonEntry>>,
    val deliveryNote: List<Pair<String, FirestoreDeliveryNote>>
)
