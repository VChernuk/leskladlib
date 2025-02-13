package com.kophe.leskladlib.repository.issuance

import com.google.firebase.firestore.WriteBatch
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonInfoItem
import com.kophe.leskladlib.datasource.firestore.FirestoreItem
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.ItemQuantity
import com.kophe.leskladlib.searchItemByFirestoreId
import com.kophe.leskladlib.searchParentQuantityItem
import java.util.Date

@Throws(IllegalArgumentException::class)
internal suspend fun DefaultIssuanceRepository.dealWithQuantityItems(
    issuanceInfoContainer: IssuanceInfoContainer,
    quantityItems: List<Item>,
    writeBatch: WriteBatch,
    forceDivideQuantityItems: Boolean = false
): List<FirestoreCommonInfoItem> {
    log("dealWithQuantityItems issuanceInfoContainer: $issuanceInfoContainer quantityItems: $quantityItems forceDivideQuantityItems: $forceDivideQuantityItems")
    val result = mutableListOf<FirestoreCommonInfoItem>()
    quantityItems.forEach { item ->
        val itemQuantity = item.quantity ?: return@forEach
        val firestoreId = item.firestoreId ?: throw IllegalArgumentException("no firestore id")
        val originalItem = itemsRepository.searchItemByFirestoreId(firestoreId)
            ?: throw IllegalArgumentException("no original item")
        if (originalItem.location == issuanceInfoContainer.location && originalItem.sublocation == issuanceInfoContainer.sublocation && originalItem.responsibleUnit == issuanceInfoContainer.responsibleUnit) {
            log("dealWithQuantityItems ... tried to move $item to same location")
            result.add(FirestoreCommonInfoItem(item.titleString(), item.firestoreId))
            return@forEach
        }
        log("dealWithQuantityItems ... originalItem: $originalItem")
        if (originalItem.quantity?.quantity == itemQuantity.quantity) {
            log("dealWithQuantityItems ... originalItem.quantity?.quantity == itemQuantity.quantity")
            itemsRepository.searchParentQuantityItem(
                itemQuantity.parentId ?: return@forEach,
                issuanceInfoContainer.location,
                sublocation = issuanceInfoContainer.sublocation,
                responsibleUnit = issuanceInfoContainer.responsibleUnit
            )?.let { parentItem ->
                val parentId = parentItem.firestoreId ?: return@forEach
                val parentQuantity = parentItem.quantity ?: return@forEach
                if (parentItem == item) return@forEach
                // 2. all quantity, parent receiver exists
                log("dealWithQuantityItems ... all quantity, parent receiver exists, parentItem: $parentItem")
                result.add(
                    dealWithQuantityItemAllQuantityWithParent(
                        item, writeBatch, parentId, parentQuantity
                    )
                )
            } ?: run {
                // 1. all quantity, no available parent receiver - done
                log("dealWithQuantityItems ... all quantity, no parent receiver")
                result.add(
                    dealWithQuantityItemAllQuantityNoParent(
                        issuanceInfoContainer, item, firestoreId, writeBatch
                    )
                )
            }
        } else if (originalItem.quantity!!.quantity > itemQuantity.quantity) {
            if (forceDivideQuantityItems) {
                result.add(forceDivideItem(writeBatch, item, originalItem, issuanceInfoContainer))
                return@forEach
            }
            itemsRepository.searchParentQuantityItem(
                itemQuantity.parentId!!,
                issuanceInfoContainer.location,
                sublocation = issuanceInfoContainer.sublocation,
                responsibleUnit = issuanceInfoContainer.responsibleUnit
            )?.let { parentItem ->
                //4. some quantity, parent receiver exists
                log("dealWithQuantityItems ... some quantity, parent receiver exists, parentItem: $parentItem")
                result.add(
                    dealWithQuantityItemSomeQuantityWithParent(
                        item, parentItem, originalItem, writeBatch
                    )
                )
                return@forEach
            } ?: run {
                //3. some quantity, no parent receiver - done
                log("dealWithQuantityItems ... some quantity, no parent receiver")
                result.add(
                    dealWithQuantityItemSomeQuantityNoParent(
                        item, originalItem, issuanceInfoContainer, writeBatch
                    )
                )
            }
        } else {
            //MORE QUANTITY THAN AVAILABLE!!
            throw IllegalArgumentException("not enought items")
        }
    }
    return result
}

internal fun DefaultIssuanceRepository.forceDivideItem(
    writeBatch: WriteBatch,
    item: Item,
    originalItem: Item,
    issuanceInfoContainer: IssuanceInfoContainer
) = dealWithQuantityItemSomeQuantityNoParent(item, originalItem, issuanceInfoContainer, writeBatch)

internal fun DefaultIssuanceRepository.updateQuantity(
    writeBatch: WriteBatch, firestoreId: String, newQuantity: Int
) {
    writeBatch.update(itemsCollection.document(firestoreId), "quantity.quantity", newQuantity)
}

internal fun DefaultIssuanceRepository.dealWithQuantityItemAllQuantityNoParent(
    issuanceInfoContainer: IssuanceInfoContainer,
    item: Item,
    firestoreId: String,
    writeBatch: WriteBatch
): FirestoreCommonInfoItem {
    log("dealWithQuantityItemAllQuantityNoParent item = $item firestoreId = $firestoreId ...")
    moveItem(
        writeBatch, firestoreId, issuanceInfoContainer
    )
    return FirestoreCommonInfoItem(item.titleString(), firestoreId)
}

internal fun DefaultIssuanceRepository.dealWithQuantityItemSomeQuantityNoParent(
    item: Item,
    originalItem: Item,
    issuanceInfoContainer: IssuanceInfoContainer,
    writeBatch: WriteBatch
): FirestoreCommonInfoItem {
    log("dealWithQuantityItemSomeQuantityNoParent item = $item issuanceCOntainer: $issuanceInfoContainer")
    val date = Date()
    val key = key(date, item.titleString())
    item.location = issuanceInfoContainer.location
    item.sublocation = issuanceInfoContainer.sublocation
    item.responsibleUnit = issuanceInfoContainer.responsibleUnit
    item.firestoreId = key
    writeBatch.set(itemsCollection.document(key), FirestoreItem(item, date))
    updateQuantity(
        writeBatch,
        originalItem.firestoreId!!,
        originalItem.quantity!!.quantity - item.quantity!!.quantity
    )
    return FirestoreCommonInfoItem(item.titleString(), key)
}

internal fun DefaultIssuanceRepository.dealWithQuantityItemAllQuantityWithParent(
    item: Item, writeBatch: WriteBatch, parentId: String, parentQuantity: ItemQuantity
): FirestoreCommonInfoItem {
    log("dealWithQuantityItemAllQuantityWithParent item = $item ...")
    updateQuantity(
        writeBatch, parentId, parentQuantity.quantity + item.quantity!!.quantity
    )
    writeBatch.delete(itemsCollection.document(item.firestoreId!!))
    return FirestoreCommonInfoItem(item.titleString(), parentId)
}

internal fun DefaultIssuanceRepository.dealWithQuantityItemSomeQuantityWithParent(
    item: Item,
    parentItem: Item,
    originalItem: Item,
    writeBatch: WriteBatch,
): FirestoreCommonInfoItem {
    log("dealWithQuantityItemSomeQuantityWithParent item = $item ...")
    updateQuantity(
        writeBatch,
        originalItem.firestoreId!!,
        originalItem.quantity!!.quantity - item.quantity!!.quantity
    )
    updateQuantity(
        writeBatch,
        parentItem.firestoreId!!,
        parentItem.quantity!!.quantity + item.quantity!!.quantity
    )
    return FirestoreCommonInfoItem(item.titleString(), parentItem.firestoreId!!)
}
