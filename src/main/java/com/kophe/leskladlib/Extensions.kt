package com.kophe.leskladlib

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ktx.toObject
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.ResponsibleUnit
import com.kophe.leskladlib.repository.common.Sublocation
import com.kophe.leskladlib.repository.items.DefaultItemsRepository
import com.kophe.leskladlib.repository.items.ItemsRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date

fun Item.isSet() = category?.id.equals("sets", true)

internal fun ItemsRepository.searchParentQuantityItem(
    parentId: String,
    location: Location,
    sublocation: Sublocation?,
    responsibleUnit: ResponsibleUnit?
) = (this as? DefaultItemsRepository)?.cachedItems?.find {
    it.quantity?.parentId == parentId && it.location == location && it.sublocation == sublocation && it.responsibleUnit == responsibleUnit
}

internal suspend fun ItemsRepository.searchItemByFirestoreId(firestoreId: String) =
    (this as? DefaultItemsRepository)?.firestoreItems?.whereEqualTo(
        FieldPath.documentId(), firestoreId
    )?.limit(1)?.get()?.await()?.firstNotNullOfOrNull { document ->
        firestoreItemToItem(document.toObject(), document.id)
    }

internal suspend fun ItemsRepository.createItem(item: Item) =
    (this as? DefaultItemsRepository)?.createItem(item)

const val TIME_FORMAT = "dd.MM.yyyy, HH:mm"
fun Long.timestampToFormattedDate24h(): String = SimpleDateFormat(TIME_FORMAT).format(Date(this))

fun Any.loggingTag() = this::class.simpleName ?: ""

fun String.validated() = removePrefix(" ").removeSuffix(" ") //TODO: use in constructors

fun String.replaceUnsupportedChars() =
    this.replace(Regex("[^a-zA-Z\\d]"), "_").replace(Regex("_{2,}"), "_")

fun String.transliterate(): String {
    val symbols = mapOf(
        'а' to "a",
        'б' to "b",
        'в' to "v",
        'г' to "h",
        'ґ' to "g",
        'д' to "d",
        'е' to "e",
        'є' to "ie",
        'ж' to "zh",
        'з' to "z",
        'и' to "y",
        'і' to "i",
        'й' to "i",
        'к' to "k",
        'л' to "l",
        'м' to "m",
        'н' to "n",
        'о' to "o",
        'п' to "p",
        'р' to "r",
        'с' to "s",
        'т' to "t",
        'у' to "u",
        'ф' to "f",
        'х' to "h",
        'ц' to "ts",
        'ш' to "sh",
        'ч' to "ch",
        'щ' to "shch",
        'ю' to "iu",
        'я' to "ia",
    )
    val builder = StringBuilder()
    for (element in this) builder.append(
        symbols.getOrDefault(
            element.lowercaseChar(), element.lowercaseChar()
        )
    )
    return builder.toString()
}
