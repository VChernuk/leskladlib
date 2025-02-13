package com.kophe.leskladlib.repository.items

import com.kophe.leskladlib.repository.common.Filter
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.TaskResult

//TODO: use flows
interface ItemsRepository {

    suspend fun precacheValues(precacheItems: Boolean = true)

    suspend fun filterItems(filter: Filter): TaskResult<List<Item>, LSError>

    suspend fun findByBarcode(barcode: String): TaskResult<List<Item>, LSError>

    suspend fun allItems(): TaskResult<List<Item>, LSError>

    suspend fun tryGetCachedItems(): TaskResult<List<Item>, LSError>

    suspend fun findByFirebaseId(id: String): TaskResult<Item, LSError>

    suspend fun findByFirebaseId(ids: List<String>): TaskResult<List<Item>, LSError>

    suspend fun createItem(
        item: Item, checkUniqueId: Boolean, checkUniqueSN: Boolean
    ): TaskResult<*, LSError>

    suspend fun createItemAndGetId(item: Item): TaskResult<Item, LSError>

    suspend fun editItem(item: Item): TaskResult<*, LSError>

    suspend fun deleteItem(item: Item): TaskResult<*, LSError>
    suspend fun setupItemsHistory(
        from: String,
        to: String,
        date: String,
        items: List<Item>,
        issuanceId: String,
        receiverCallSign: String?
    ): Any?

}
