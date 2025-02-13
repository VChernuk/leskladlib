package com.kophe.leskladlib.repository.categories

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source.CACHE
import com.google.firebase.firestore.Source.DEFAULT
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.kophe.leskladlib.connectivity.ConnectionStateMonitor
import com.kophe.leskladlib.datasource.firestore.FirestoreCategory
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonEntry
import com.kophe.leskladlib.datasource.firestore.FirestoreSubcategory
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.replaceUnsupportedChars
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.Category
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.Subcategory
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import com.kophe.leskladlib.transliterate
import com.kophe.leskladlib.validated
import kotlinx.coroutines.tasks.await
import java.util.Date

class DefaultCategoriesRepository(
    loggingUtil: LoggingUtil,
    builder: RepositoryBuilder,
    private val connection: ConnectionStateMonitor
) : CategoriesRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreCategories by lazy { db.collection(builder.categoriesCollection) }
    private val firestoreSubcategories by lazy { db.collection(builder.subcategoriesCollection) }
    private val localCachedCategories = mutableSetOf<Category>()
    private val localCachedSubcategories = mutableSetOf<Subcategory>()

    override suspend fun precacheValues() {
        if (localCachedSubcategories.isEmpty()) allSubcategories()
        if (localCachedCategories.isEmpty()) allCategories(forceReload = false, includeSets = true)
    }

    private suspend fun allSubcategories() = try {
        firestoreSubcategories.get(if (connection.available() != true) CACHE else DEFAULT).await()
            .mapNotNull { result ->
                val item = result.toObject<FirestoreSubcategory>()
                val subcategory =
                    item.title?.let { Subcategory(it, result.id, item.subcategory_weight ?: 0) }
                        ?: return@mapNotNull null
                localCachedSubcategories.add(subcategory)
            }
    } catch (e: Exception) {
        log("allSubcategories(...) failed due to: ${e.message}")
    }

    override suspend fun allCategories(
        forceReload: Boolean, includeSets: Boolean
    ): TaskResult<List<Category>, LSError> = try {
        log("allCategories(...)")
        if (forceReload || localCachedCategories.isEmpty() || localCachedSubcategories.isEmpty()) {
            localCachedSubcategories.clear()
            localCachedCategories.clear()
            allSubcategories()
            TaskSuccess(firestoreCategories.get(if (connection.available() != true) CACHE else DEFAULT)
                .await().mapNotNull { result ->
                    log("${result.id} => ${result.data}")
                    val item = result.toObject<FirestoreCategory>()
                    log("category: $item")
                    val title = item.title ?: return@mapNotNull null
                    val category = Category(title,
                        item.subcategories?.let { list -> getSubcategories(list) } ?: emptyList(),
                        result.id,
                        item.category_weight ?: 500)
                    localCachedCategories.add(category)
                    if (includeSets) return@mapNotNull category
                    if (category.id != "sets") category
                    else return@mapNotNull null
                })
        } else TaskSuccess(if (includeSets) localCachedCategories.toList() else localCachedCategories.toList()
            .filter { it.id != "sets" })

    } catch (e: Exception) {
        log("allCategories(...) failed due to: ${e.message}")
        TaskError(LSError.SimpleError("${e.message}"))
    }

    private suspend fun getSubcategories(ids: List<String>): List<Subcategory> {
        log("getSubcategories(...): ids=$ids")
        val subcategories = if (ids.isEmpty()) emptyList()
        else if (localCachedSubcategories.map { it.id }
                .containsAll(ids)) localCachedSubcategories.filter { ids.contains(it.id) }
        else if (ids.size > 10) {
            allSubcategories()
            localCachedSubcategories.filter { ids.contains(it.id) }
        } else try {
            firestoreSubcategories.whereIn(FieldPath.documentId(), ids)
                .get(if (connection.available() != true) CACHE else DEFAULT).await()
                .mapNotNull { result ->
                    val item = result.toObject<FirestoreSubcategory>()
                    val subcategory = item.title?.let {
                        Subcategory(
                            it, result.id, item.subcategory_weight ?: 500
                        )
                    } ?: return@mapNotNull null
                    localCachedSubcategories.add(subcategory)
                    subcategory
                }
        } catch (e: Exception) {
            log("getSubcategories(...) failed due to: ${e.message}")
            return emptyList() //TODO: review
        }
        return subcategories
    }

    override suspend fun getCategory(id: String): Category? =
        localCachedCategories.find { it.id == id } ?: firestoreCategories.whereEqualTo(
            FieldPath.documentId(), id
        ).limit(1).get(if (connection.available() != true) CACHE else DEFAULT).await().firstOrNull()
            ?.let { result ->
                val item = result.toObject<FirestoreCategory>()
                val title = item.title ?: return@let null
                val category = Category(title,
                    item.subcategories?.let { list -> getSubcategories(list) } ?: emptyList(),
                    id = result.id,
                    item.category_weight ?: 0)
                localCachedCategories.add(category)
                category
            }

    override suspend fun updateCategory(category: Category): TaskResult<Any, LSError> {
        log("updateCategory(...): title=${category.title}")
        val title = category.title
        val batchWriteBatch = db.batch()
        return try {
            val subcategoryKeys = mutableSetOf<String>()
            category.subcategories.forEach {
                val subcategoryKey = it.id.ifEmpty {
                    (title + "_" + it.title + "_${Date().time}").validated().transliterate()
                        .replaceUnsupportedChars()
                }
                batchWriteBatch.set(
                    firestoreSubcategories.document(subcategoryKey), FirestoreCommonEntry(it.title)
                )
                subcategoryKeys.add(subcategoryKey)
            }
            val key = category.id.ifEmpty {
                title.validated().transliterate().replaceUnsupportedChars()
            }
            batchWriteBatch.set(
                firestoreCategories.document(key),
                FirestoreCategory(title, subcategoryKeys.toList(), category.weight)
            )
            TaskSuccess(batchWriteBatch.commit().await())
        } catch (e: Exception) {
            log("createItem(...) failed due to: ${e.message}")
            TaskError<Any, LSError>(LSError.SimpleError("${e.message}"))
        }
    }

}
