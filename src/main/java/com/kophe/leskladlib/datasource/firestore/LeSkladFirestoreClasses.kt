package com.kophe.leskladlib.datasource.firestore

import com.google.firebase.Timestamp
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.validated
import java.util.Date

internal data class FirestoreCommonInfoItem(
    val title: String? = null, val firestore_id: String? = null
)

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
    val delivery_note_number: String? = null,
    val delivery_note_date: String? = null,
    val from: String? = null,
    val to: String? = null,
    val date: String? = null,
    val date_timestamp: com.google.firebase.Timestamp? = null,
    val deliverynote_items: List<FirestoreCommonInfoItem>? = null,
    val to_location_id: String? = null,
    val notes: String? = null,
    val to_sublocation_id: String? = null,
    val responsible_unit_id: String? = null,
    val receiver_call_sign: String? = null,
    val user_email: String? = null
)

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
    val deliverynote: List<Pair<String, FirestoreDeliveryNote>>,
    val locations: List<Pair<String, FirestoreLocation>>,
    val sublocations: List<Pair<String, FirestoreCommonEntry>>,
    val categories: List<Pair<String, FirestoreCategory>>,
    val subcategories: List<Pair<String, FirestoreSubcategory>>,
    val items: List<Pair<String, FirestoreItem>>,
    val ownershipTypes: List<Pair<String, FirestoreCommonEntry>>,
    val users: List<Pair<String, FirestoreCommonEntry>>,
    val duty: List<Pair<String, FirestoreDuty>>?,
    val responsibleUnits: List<Pair<String, FirestoreCommonEntry>>?,
    val adminUsers: List<Pair<String, FirestoreCommonEntry>>
)
