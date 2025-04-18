package com.kophe.leskladlib.repository.common

import android.os.Parcelable
import androidx.annotation.Keep
import com.kophe.leskladlib.TIME_FORMAT
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonInfoItem
import com.kophe.leskladlib.datasource.firestore.FirestoreItem
import com.kophe.leskladlib.timestampToFormattedDate24h
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat

//TODO: deal with errors
sealed class LSError(val message: String? = null) {

    class SimpleError(message: String? = null) : LSError(message)

}

@Keep
@Parcelize
data class Filter(
    var location: Location? = null,
    var sublocation: Sublocation? = null,
    var category: Category? = null,
    var subcategory: Subcategory? = null,
    var ownershipType: OwnershipType? = null,
    var responsibleUnit: ResponsibleUnit? = null,
    var deliveryNote: DeliveryNote? = null
) : Parcelable {
    val filterDescription: String
        get() {
            return "_" + (location?.title ?: "") + "_" + (category?.title
                ?: "") + "_" + (subcategory?.title ?: "") + "_" + (ownershipType?.title
                ?: "") + "_" + (responsibleUnit?.title ?: "")
        }

    fun isClear() =
        location == null && category == null && subcategory == null && ownershipType == null && sublocation == null && responsibleUnit == null

}

@Keep
@Parcelize
data class Duty(
    val user: String,
    val sublocation: Sublocation,
    val approvedItems: List<DutyItem>,
    val declinedItems: List<DutyItem>,
    val date: String?
) : Parcelable

@Keep
@Parcelize
data class DutyItem(
    val title: String?, val id: String?, val barcode: String?, val firestoreId: String
) : Parcelable

@Keep
@Parcelize
data class Issuance(
    val from: String,
    val to: String,
    val date: String,
    val items: List<CommonItem>,
    val notes: String,
    val responsibleUnit: ResponsibleUnit?,
    val receiverCallSign: String?
) : Parcelable, Comparable<Issuance> {

    override fun compareTo(other: Issuance): Int {
        val format = SimpleDateFormat(TIME_FORMAT)
        val thisDate = format.parse(date) ?: return date.compareTo(other.date)
        val otherDate = format.parse(other.date) ?: return date.compareTo(other.date)
        return thisDate.compareTo(otherDate)
    }

}

@Keep
@Parcelize
data class DeliveryNote(
    var id: String?,
    val deliveryNoteNumber: String?,
    val deliveryNoteDate: String?,
    val deliveryNotePIB: String?,
    val from: String,
    val to: String,
    val date: String,
    val items: List<CommonItem>,
    val notes: String,
    val responsibleUnit: ResponsibleUnit?,
    val receiverCallSign: String?
) : Parcelable, Comparable<DeliveryNote> {

    override fun compareTo(other: DeliveryNote): Int {
        val format = SimpleDateFormat(TIME_FORMAT)
        val thisDate = format.parse(date) ?: return date.compareTo(other.date)
        val otherDate = format.parse(other.date) ?: return date.compareTo(other.date)
        return thisDate.compareTo(otherDate)
    }

}

@Keep
@Parcelize
data class CommonItem(val title: String?, val firestoreId: String?) : Parcelable {

    internal constructor(firestoreCommonInfoItem: FirestoreCommonInfoItem) : this(
        firestoreCommonInfoItem.title, firestoreCommonInfoItem.firestore_id
    )

    override fun equals(other: Any?) =
        (other as? Item)?.let { it.firestoreId == firestoreId && it.title == title }
            ?: super.equals(other)

    override fun hashCode() = firestoreId.hashCode()

}

@Keep
@Parcelize
data class ItemImage(val url: String, val date: String?) : Parcelable

@Keep
@Parcelize
data class ItemSetOptions(
    val parentItem: CommonItem?, var subItems: Set<Item>
) : Parcelable

@Keep
@Parcelize
data class ItemQuantity(var parentId: String?, val quantity: Int, val measurement: String) :
    Parcelable {
    override fun hashCode() = parentId.hashCode()

    override fun equals(other: Any?) = (other as? ItemQuantity)?.let {
        !it.parentId.isNullOrEmpty() && it.parentId.equals(parentId, true)
    } ?: false

    override fun toString() = "$quantity $measurement"
}

//TODO: validate new fields
@Keep
@Parcelize
data class Item(
    var barcode: String?,
    var id: String?,
    var title: String?,
    var location: Location?,
    var category: Category?,
    var subcategories: List<Subcategory> = emptyList(),
    var notes: String?,
    var firestoreId: String?,
    var createdDate: String?,
    var history: List<CommonItem> = emptyList(),
    var ownershipType: OwnershipType?,
    var deliveryNote: DeliveryNote?,
    var images: MutableList<ItemImage>?,
    var sublocation: Sublocation?,
    var responsibleUnit: ResponsibleUnit?,
    var sn: String?,
    var quantity: ItemQuantity?,
    var setOptions: ItemSetOptions?
) : Parcelable {

    constructor() : this(
        barcode = null,
        id = null,
        title = null,
        location = null,
        category = null,
        notes = null,
        firestoreId = null,
        createdDate = null,
        ownershipType = null,
        deliveryNote = null,
        images = mutableListOf(),
        sublocation = null,
        responsibleUnit = null,
        sn = null,
        quantity = null,
        setOptions = null
    )

    constructor(commonItem: CommonItem) : this(
        title = commonItem.title,
        firestoreId = commonItem.firestoreId,
        category = null,
        location = null,
        notes = null,
        createdDate = null,
        id = null,
        barcode = null,
        ownershipType = null,
        deliveryNote = null,
        images = mutableListOf(),
        sublocation = null,
        responsibleUnit = null,
        sn = null,
        quantity = null,
        setOptions = null
    )

    internal constructor(
        firestoreItem: FirestoreItem,
        location: Location?,
        sublocation: Sublocation?,
        category: Category?,
        ownershipType: OwnershipType?,
        deliveryNote: DeliveryNote?,
        firestoreId: String?,
        responsibleUnit: ResponsibleUnit?,
        quantity: ItemQuantity?,
        setOptions: ItemSetOptions?
    ) : this(barcode = firestoreItem.barcode,
        title = firestoreItem.title,
        category = category,
        location = location,
        subcategories = category?.subcategories?.filter {
            firestoreItem.subcategory_ids?.contains(it.id) ?: false
        } ?: emptyList(),
        notes = firestoreItem.notes,
        id = firestoreItem.id,
        firestoreId = firestoreId,
        createdDate = firestoreItem.date_created?.seconds?.times(1000)
            ?.timestampToFormattedDate24h(),
        history = firestoreItem.history?.mapNotNull {
            CommonItem(
                it.title ?: return@mapNotNull null, it.firestore_id ?: return@mapNotNull null
            )
        } ?: emptyList(),
        ownershipType = ownershipType,

        deliveryNote = deliveryNote,

        images = firestoreItem.item_images?.mapNotNull {
            ItemImage(
                it.path ?: return@mapNotNull null, ""
            )
        }?.toMutableList() ?: mutableListOf(),
        sublocation = sublocation,
        responsibleUnit = responsibleUnit,
        sn = firestoreItem.serial_number,
        quantity = quantity,
        setOptions = setOptions
    )

    override fun equals(other: Any?) =
        (other as? Item)?.let { it.firestoreId == firestoreId && it.title == title && it.id == id && it.barcode == barcode && it.sn == sn && responsibleUnit?.id == other.responsibleUnit?.id }
            ?: super.equals(other)

    override fun hashCode() = firestoreId.hashCode()

    override fun toString() =
        "${titleString()} $firestoreId ${category?.id} ${subcategories} ${location?.id} ${sublocation?.id}  "

    fun titleString() = (title?.let { "$it " } ?: "") + (id?.let { "$it " }
        ?: "") + (barcode?.let { if (it != title) "$it " else "" }
        ?: "") + (sn?.let { if (it != barcode && it != title) "$it " else "" } ?: "") + (quantity
        ?: "")

    fun toCommonItem() = firestoreId?.let { CommonItem(titleString(), it) }

}

@Keep
@Parcelize
data class OwnershipType(val title: String, val id: String) : Parcelable {
    override fun equals(other: Any?) =
        (other as? OwnershipType)?.let { it.id == id && it.title == title } ?: super.equals(other)

    override fun hashCode() = id.hashCode()
}

@Keep
@Parcelize
data class Category(
    val title: String,
    val subcategories: List<Subcategory> = emptyList(),
    val id: String,
    val weight: Int
) : Parcelable, Comparable<Category> {
    override fun compareTo(other: Category) = weight.compareTo(other.weight)

    override fun equals(other: Any?) =
        (other as? Category)?.let { it.id == id && it.title == title } ?: super.equals(other)

    override fun hashCode() = id.hashCode()

    override fun toString() = "$title $id $subcategories"
}

@Keep
@Parcelize
data class Subcategory(val title: String, val id: String, val weight: Int) : Parcelable,
    Comparable<Subcategory> {
    override fun compareTo(other: Subcategory) = weight.compareTo(other.weight)

    override fun equals(other: Any?) =
        (other as? Subcategory)?.let { it.id == id && it.title == title } ?: super.equals(other)

    override fun hashCode() = id.hashCode()
}

@Keep
@Parcelize
data class Location(val title: String, val id: String, val sublocations: List<Sublocation>) :
    Parcelable {

    override fun equals(other: Any?) =
        (other as? Location)?.let { it.id == id && it.title == title } ?: super.equals(other)

    override fun hashCode() = id.hashCode()

}

@Keep
@Parcelize
data class ResponsibleUnit(val title: String, val id: String) : Parcelable {

    override fun equals(other: Any?) =
        (other as? ResponsibleUnit)?.let { it.id == id && it.title == title } ?: super.equals(other)

    override fun hashCode() = id.hashCode()

}


@Keep
@Parcelize
data class Sublocation(val title: String, val id: String) : Parcelable {

    override fun equals(other: Any?) =
        (other as? Sublocation)?.let { it.id == id && it.title == title } ?: super.equals(other)

    override fun hashCode() = id.hashCode()

}
