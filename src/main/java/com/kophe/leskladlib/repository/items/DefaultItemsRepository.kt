package com.kophe.leskladlib.repository.items

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath.documentId
import com.google.firebase.firestore.Query.Direction.DESCENDING
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonInfoItem
import com.kophe.leskladlib.datasource.firestore.FirestoreItem
import com.kophe.leskladlib.datasource.firestore.FirestoreItemImage
import com.kophe.leskladlib.datasource.firestore.FirestoreQuantity
import com.kophe.leskladlib.datasource.firestore.FirestoreSetOptions
import com.kophe.leskladlib.datasource.firestore.ITEM_BARCODE
import com.kophe.leskladlib.datasource.firestore.ITEM_CATEGORY_ID
import com.kophe.leskladlib.datasource.firestore.ITEM_DATE_CREATED
import com.kophe.leskladlib.datasource.firestore.ITEM_DATE_MODIFIED
import com.kophe.leskladlib.datasource.firestore.ITEM_HISTORY
import com.kophe.leskladlib.datasource.firestore.ITEM_ID
import com.kophe.leskladlib.datasource.firestore.ITEM_IMAGES
import com.kophe.leskladlib.datasource.firestore.ITEM_LOCATION_ID
import com.kophe.leskladlib.datasource.firestore.ITEM_NOTES
import com.kophe.leskladlib.datasource.firestore.ITEM_OWNERSHIP_TYPE_ID
import com.kophe.leskladlib.datasource.firestore.ITEM_QUANTITY
import com.kophe.leskladlib.datasource.firestore.ITEM_RESPONSIBLE_UNIT_ID
import com.kophe.leskladlib.datasource.firestore.ITEM_SERIAL_NUMBER
import com.kophe.leskladlib.datasource.firestore.ITEM_SET_OPTIONS
import com.kophe.leskladlib.datasource.firestore.ITEM_SET_OPTIONS_PARENT
import com.kophe.leskladlib.datasource.firestore.ITEM_SUBCATEGORY_IDS
import com.kophe.leskladlib.datasource.firestore.ITEM_SUBLOCATION_ID
import com.kophe.leskladlib.datasource.firestore.ITEM_TITLE
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.categories.CategoriesRepository
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.CommonItem
import com.kophe.leskladlib.repository.common.Filter
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.ItemQuantity
import com.kophe.leskladlib.repository.common.ItemSetOptions
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import com.kophe.leskladlib.repository.locations.LocationsRepository
import com.kophe.leskladlib.repository.ownership.OwnershipRepository
import com.kophe.leskladlib.repository.units.UnitsRepository
import com.kophe.leskladlib.validated
import kotlinx.coroutines.tasks.await
import java.util.Date

//TODO: validate quantity fields
//TODO: use live data and caching
class DefaultItemsRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val locationsRepository: LocationsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val ownershipRepository: OwnershipRepository,
    private val unitsRepository: UnitsRepository?
) : ItemsRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val localCachedExistingIds = mutableSetOf<String>()
    internal val firestoreItems by lazy { db.collection(builder.itemsCollection) }
    internal val cachedItems = mutableListOf<Item>()

    override suspend fun precacheValues(precacheItems: Boolean) {
        locationsRepository.precacheValues()
        categoriesRepository.precacheValues()
        ownershipRepository.precacheValues()
        unitsRepository?.precacheValues()
        if (precacheItems) allItems()
    }

    //TODO: fields constants
    override suspend fun filterItems(filter: Filter): TaskResult<List<Item>, LSError> {
        //TODO: unit
        log("filterItems(...): filter=$filter")
        if (filter.isClear()) return allItems()
        var query = firestoreItems.orderBy(ITEM_DATE_CREATED, DESCENDING)
        filter.location?.id?.let {
            if (it.isNotEmpty()) query = query.whereEqualTo(ITEM_LOCATION_ID, it)
        }
        filter.category?.id?.let {
            if (it.isNotEmpty()) query = query.whereEqualTo(ITEM_CATEGORY_ID, it)
        }
        filter.subcategory?.id?.let {
            if (it.isNotEmpty()) query = query.whereArrayContains(ITEM_SUBCATEGORY_IDS, it)
        }
        filter.ownershipType?.id?.let {
            if (it.isNotEmpty()) query = query.whereEqualTo(ITEM_OWNERSHIP_TYPE_ID, it)
        }
        filter.sublocation?.id?.let {
            if (it.isNotEmpty()) query = query.whereEqualTo(ITEM_SUBLOCATION_ID, it)
        }
        filter.responsibleUnit?.id?.let {
            if (it.isNotEmpty()) query = query.whereEqualTo(ITEM_RESPONSIBLE_UNIT_ID, it)
        }
        return try {
            TaskSuccess(query.get().await()
                .mapNotNull { firestoreItemToItem(it.toObject(), it.id) })
        } catch (e: Exception) {
            log("filterItems(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }
    }

    override suspend fun findByFirebaseId(ids: List<String>): TaskResult<List<Item>, LSError> {
        log("findByFirebaseIds(...): id=$ids")
        if (ids.isEmpty()) return TaskError(SimpleError("no id provided"))
        if (cachedItems.isEmpty()) allItems()
        val cachedEntries = cachedItems.filter { item -> ids.contains(item.firestoreId) }
        if (cachedEntries.size == ids.size) return TaskSuccess(cachedEntries)
        return try {
            TaskSuccess(
                firestoreItems.whereIn(documentId(), ids).get().await().mapNotNull { document ->
                    log("${document.id} => ${document.data}")
                    firestoreItemToItem(document.toObject(), document.id)
                })
        } catch (e: Exception) {
            log("findByFirebaseIds(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }
    }

    private suspend fun findRawItemsByFirestoreIds(ids: List<String>): List<Item> {
        val cachedEntries = cachedItems.filter { item -> ids.contains(item.firestoreId) }
        if (cachedEntries.size == ids.size) return cachedEntries
        return firestoreItems.whereIn(documentId(), ids).get().await().mapNotNull { document ->
            firestoreItemToItem(document.toObject(), document.id)
        }
    }

    override suspend fun findByFirebaseId(id: String): TaskResult<Item, LSError> {
        log("findByFirebaseId(...): id=$id")
        if (id.isEmpty()) return TaskError(SimpleError("no id provided"))
        if (cachedItems.isEmpty()) allItems()
        cachedItems.firstOrNull { it.firestoreId.equals(id, true) }?.let { return TaskSuccess(it) }
        return try {
            TaskSuccess(firestoreItems.whereEqualTo(documentId(), id).limit(1).get().await()
                .firstNotNullOfOrNull { document ->
                    log("${document.id} => ${document.data}")
                    firestoreItemToItem(document.toObject(), document.id)
                } ?: return TaskError(SimpleError("no item found")))
        } catch (e: Exception) {
            log("findByFirebaseId(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }
    }

    override suspend fun findByBarcode(barcode: String): TaskResult<List<Item>, LSError> {
        log("findByBarcode(...): barcode=$barcode")
        if (barcode.isEmpty()) return TaskError(SimpleError("no barcode provided"))
        return try {
            TaskSuccess(firestoreItems.whereEqualTo(ITEM_BARCODE, barcode).get().await()
                .mapNotNull { document ->
                    log("${document.id} => ${document.data}")
                    firestoreItemToItem(document.toObject(), document.id)
                })
        } catch (e: Exception) {
            log("findByBarcode(...) failed due to: ${e.message}")
            TaskError(SimpleError("${e.message}"))
        }
    }

    internal suspend fun firestoreItemToItem(
        item: FirestoreItem, documentId: String, searchDuplicate: Boolean = true
    ): Item? {
        log("firestoreItemToItem: $item")
        val setOptions = setOptions(item.set_options)
        val newItem = Item(
            firestoreItem = item,
            category = findCategory(item) ?: return null,
            location = findLocation(item) ?: return null,
            sublocation = findSublocation(item),
            firestoreId = documentId,
            ownershipType = findOwnershipType(item),
            responsibleUnit = findResponsibleUnit(item),
            quantity = item.quantity?.let {
                ItemQuantity(
                    it.parent_id ?: return@let null,
                    it.quantity ?: return@let null,
                    it.measurement ?: return@let null
                )
            },
            setOptions = setOptions,
        )
        if (searchDuplicate) cachedItems.removeIf {
//            log("remove if: $it $newItem")
            it.firestoreId != null && newItem.firestoreId != null && it.firestoreId.equals(newItem.firestoreId)
        }
        cachedItems.add(newItem)
        return newItem
    }

    private suspend fun setOptions(options: FirestoreSetOptions?) = options?.let {
        ItemSetOptions(
            it.parent_set_id?.let { id -> CommonItem(null, id) },
            findRawItemsByFirestoreIds(it.subItemIds ?: emptyList()).toSet()
        )
    }

    private suspend fun findCategory(item: FirestoreItem) =
        item.category_id?.let { categoriesRepository.getCategory(it) }

    private suspend fun findLocation(item: FirestoreItem) =
        item.location_id?.let { locationsRepository.getLocation(it) }

    private suspend fun findSublocation(item: FirestoreItem) =
        item.sublocation_id?.let { locationsRepository.getSublocation(it) }

    private suspend fun findOwnershipType(item: FirestoreItem) =
        item.ownership_type_id?.let { ownershipRepository.getOwnershipType(it) }

    private suspend fun findResponsibleUnit(item: FirestoreItem) =
        item.responsible_unit_id?.let { unitsRepository?.getUnit(it) }

    override suspend fun tryGetCachedItems(): TaskResult<List<Item>, LSError> =
        if (cachedItems.isNotEmpty()) TaskSuccess(cachedItems) else allItems()

    override suspend fun allItems(): TaskResult<List<Item>, LSError> = try {
        log("allItems(...)")
        cachedItems.clear()
        val resultList = firestoreItems.orderBy(ITEM_DATE_CREATED, DESCENDING).get().await()
            .mapNotNull { document -> firestoreItemToItem(document.toObject(), document.id, false) }
        TaskSuccess(resultList)
    } catch (e: Exception) {
        log("getItems(...) failed due to: ${e.message}")
        TaskError(SimpleError(e.message))
    }

    override suspend fun createItem(item: Item, checkUniqueId: Boolean, checkUniqueSN: Boolean) =
        createItem(item, checkUniqueId, key(Date(), item), checkUniqueSN)

    private suspend fun createItem(
        item: Item, checkUniqueId: Boolean, key: String, checkUniqueSN: Boolean
    ): TaskResult<*, LSError> {
        log("createItem(...): item=$item, checkUniqueId: $checkUniqueId key: $key")
        try {
            if (checkUniqueId) {
                if (!item.id.isNullOrEmpty() && !uniqueId(item.id!!)) {
                    return TaskError<Any, LSError>(SimpleError("Позиція з id: ${item.id} вже існує"))
                }
            }
            if (checkUniqueSN && !item.sn.isNullOrEmpty() && !uniqueSN(item.sn!!)) {
                return TaskError<Any, LSError>(SimpleError("Позиція з серійником: ${item.sn} вже існує"))
            }
            item.quantity?.let { it.parentId = key }
            val wb = db.batch()
            wb.set(firestoreItems.document(key), FirestoreItem(item, Date()))
            item.setOptions?.subItems?.forEach { subitem ->
                wb.update(
                    firestoreItems.document(subitem.firestoreId!!), ITEM_SET_OPTIONS_PARENT, key
                )
            }
            return TaskSuccess(wb.commit().await())
        } catch (e: Exception) {
            log("createItem(...) failed due to: ${e.message}")
            return TaskError<Any, LSError>(SimpleError("${e.message}"))
        }
    }

    private suspend fun uniqueId(id: String) =
        !localCachedExistingIds.contains(id.validated()) && firestoreItems.whereEqualTo(
            "id", id.validated()
        ).get().await().isEmpty

    private suspend fun uniqueSN(sn: String) = !cachedItems.mapNotNull { it.sn }
        .contains(sn.validated()) && firestoreItems.whereEqualTo("sn", sn.validated()).get()
        .await().isEmpty

    override suspend fun createItemAndGetId(item: Item): TaskResult<Item, LSError> {
        log("createItemAndGetId(...): item=$item")
        return try {
            val newItem = createItem(item)
            TaskSuccess(newItem)
        } catch (e: Exception) {
            log("createItemAndGetId(...) getting id failed due to: ${e.message}")
            return TaskError(SimpleError("${e.message}"))
        }
    }

    //TODO: fix key
    internal suspend fun createItem(item: Item): Item {
        if (item.barcode.isNullOrEmpty() && item.title.isNullOrEmpty() && item.id.isNullOrEmpty()) throw IllegalArgumentException(
            "item.barcode.isNullOrEmpty() && item.title.isNullOrEmpty() && item.id.isNullOrEmpty()"
        )
        val key = key(Date(), item)
        val createdItemResult = createItem(item, true, key, true)
        (createdItemResult as? TaskError<*, LSError>)?.let { throw java.lang.Exception(it.error?.message) }
        val query = firestoreItems.whereEqualTo(documentId(), key).limit(1)
        return query.get().await().firstNotNullOf { document ->
            val newItem = document.toObject<FirestoreItem>()
            Item(
                firestoreItem = newItem,
                category = item.category,
                location = item.location,
                sublocation = item.sublocation,
                firestoreId = document.id,
                ownershipType = item.ownershipType,
                responsibleUnit = item.responsibleUnit,
                quantity = item.quantity,
                setOptions = item.setOptions
            )
        }
    }

    override suspend fun deleteItem(item: Item) = try {
        log("  log(\"deleteItem(...): item=$item\")(...): item=$item")
        val result = TaskSuccess<Any, LSError>(
            firestoreItems.document(item.firestoreId!!).delete().await()
        )
        cachedItems.removeIf { it.firestoreId == item.firestoreId }
        result
    } catch (e: Exception) {
        log("deleteItem(...) failed due to: ${e.message}")
        TaskError<Any, LSError>(SimpleError("${e.message}"))
    }

    //TODO: constant fields
    override suspend fun editItem(item: Item) = try {
        log("editItem(...): item=$item")
        TaskSuccess(
            firestoreItems.document(item.firestoreId!!).update(
                hashMapOf(ITEM_BARCODE to item.barcode?.validated(),
                    ITEM_TITLE to item.title?.validated(),
                    ITEM_LOCATION_ID to item.location?.id,
                    ITEM_SUBLOCATION_ID to item.sublocation?.id,
                    ITEM_CATEGORY_ID to item.category?.id,
                    ITEM_ID to item.id?.validated(),
                    ITEM_NOTES to item.notes?.validated(),
                    ITEM_SUBCATEGORY_IDS to item.subcategories.map { it.id },
                    ITEM_DATE_MODIFIED to Timestamp(Date()),
                    ITEM_HISTORY to item.history.map {
                        FirestoreCommonInfoItem(it.title, it.firestoreId)
                    },
                    ITEM_OWNERSHIP_TYPE_ID to item.ownershipType?.id,
                    ITEM_IMAGES to item.images?.map { FirestoreItemImage(it.url, "") },
                    ITEM_RESPONSIBLE_UNIT_ID to item.responsibleUnit?.id,
                    ITEM_SERIAL_NUMBER to item.sn?.validated(),
                    ITEM_QUANTITY to item.quantity?.let {
                        FirestoreQuantity(it.parentId, it.quantity, it.measurement)
                    },
                    ITEM_SET_OPTIONS to item.setOptions?.let { null })
            ).await()
        )
    } catch (e: Exception) {
        log("editItem(...) failed due to: ${e.message}")
        TaskError<Any, LSError>(SimpleError("${e.message}"))
    }

    override suspend fun setupItemsHistory(
        from: String,
        to: String,
        date: String,
        items: List<Item>,
        issuanceId: String,
        receiverCallSign: String?
    ) = try {
        log("setupItemHistory(...)")
        val writeBatch = db.batch()
        items.forEach { item ->
            val history = item.history.toMutableList()
            history.add(CommonItem("$date $from -> $to ${receiverCallSign ?: ""}", issuanceId))
            item.history = history
            writeBatch.update(firestoreItems.document(item.firestoreId!!),
                ITEM_HISTORY,
                item.history.map { FirestoreCommonInfoItem(it.title, it.firestoreId) })
        }
        writeBatch.commit().await()
    } catch (e: Exception) {
        log("setupItemHistory(...) failed due to: ${e.message}")
        //TODO: handle
    }

}
