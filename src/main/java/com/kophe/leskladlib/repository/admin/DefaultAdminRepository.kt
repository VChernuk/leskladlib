package com.kophe.leskladlib.repository.admin

import androidx.core.net.toUri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kophe.leskladlib.TIME_FORMAT
import com.kophe.leskladlib.datasource.firestore.FirestoreBackupObject
import com.kophe.leskladlib.datasource.firestore.FirestoreCategory
import com.kophe.leskladlib.datasource.firestore.FirestoreCommonEntry
import com.kophe.leskladlib.datasource.firestore.FirestoreDuty
import com.kophe.leskladlib.datasource.firestore.FirestoreIssuance
import com.kophe.leskladlib.datasource.firestore.FirestoreItem
import com.kophe.leskladlib.datasource.firestore.FirestoreLocation
import com.kophe.leskladlib.datasource.firestore.FirestoreSubcategory
import com.kophe.leskladlib.datasource.firestore.FirestoreDeliveryNote
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.DeliveryNote
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.LSError.SimpleError
import com.kophe.leskladlib.repository.common.OwnershipType
import com.kophe.leskladlib.repository.common.RepositoryBuilder
import com.kophe.leskladlib.repository.common.TaskResult
import com.kophe.leskladlib.repository.common.TaskResult.TaskError
import com.kophe.leskladlib.repository.common.TaskResult.TaskSuccess
import com.kophe.leskladlib.repository.items.ItemsRepository
import com.kophe.leskladlib.timestampToFormattedDate24h
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date


//TODO: backup duty repository
//TODO: backup users
class DefaultAdminRepository(
    loggingUtil: LoggingUtil,
    private val itemsRepository: ItemsRepository,
    builder: RepositoryBuilder,
    private val filesDir: String
) : AdminRepository, BaseRepository(loggingUtil) {

    private val db by lazy { Firebase.firestore }
    private val firestoreIssuance by lazy { db.collection(builder.issuanceCollection) }
    private val firestoreCategory by lazy { db.collection(builder.categoriesCollection) }
    private val firestoreSubcategory by lazy { db.collection(builder.subcategoriesCollection) }
    private val firestoreItems by lazy { db.collection(builder.itemsCollection) }
    private val firestoreLocation by lazy { db.collection(builder.locationsCollection) }
    private val firestoreSublocation by lazy { db.collection(builder.sublocations) }
    private val firestoreOwnership by lazy { db.collection(builder.ownershipTypesCollection) }
    private val firestoreUsers by lazy { db.collection(builder.usersCollection) }
    private val firestoreAdminUsers by lazy { db.collection(builder.adminsCollection) }
    private val firestoreDuty by lazy {
        if (builder.dutyCollection.isEmpty()) null else db.collection(builder.dutyCollection)
    }
    private val firestoreUnits by lazy {
        if (builder.unitsCollection.isEmpty()) null else db.collection(builder.unitsCollection)
    }
    private val firestoreDeliveryNote by lazy { db.collection(builder.deliveryNotesCollection) }


    override suspend fun setAllOwnerTypesToUnknown(): TaskResult<Any, LSError> = try {
        (itemsRepository.allItems() as? TaskSuccess)?.result?.let {
            it.forEach { item ->
                if (item.ownershipType == null) {
                    item.ownershipType = OwnershipType("ВЗ", "unknown")
                    (itemsRepository.editItem(item) as? TaskSuccess)?.let { log("item $item was edited successfully") }
                        ?: run { return@let TaskError(SimpleError("couldn't edit item $item")) }
                }
            }
            log("successfully edited ${it.size} items")
            return@let TaskSuccess()
        } ?: run { TaskError(SimpleError("couldn't get items")) }
    } catch (e: Exception) {
        log("setAllOwnerTypesToUnknown(...) failed due to: ${e.message}")
        TaskError(SimpleError(e.message))
    }

    override suspend fun migrateIssuanceToTimestamp(): TaskResult<Any, LSError> = try {
        firestoreIssuance.get().await().forEach { document ->
            val item = document.toObject<FirestoreIssuance>()
            if (!item.date.isNullOrEmpty()) {
                editIssuance(item, document.id)
            }
        }
        TaskSuccess()
    } catch (e: Exception) {
        log("migrateIssuanceToTimestamp(...) failed due to: ${e.message}")
        TaskError(SimpleError(e.message))
    }

    private suspend inline fun <reified T> getEntries(ref: CollectionReference) =
        ref.get().await().mapNotNull { Pair(it.id, it.toObject<T>() ?: return@mapNotNull null) }


    //TODO: backup users
    override suspend fun createBackupFile(): TaskResult<Any, LSError> {
        try {
            val issuances = getEntries<FirestoreIssuance>(firestoreIssuance)
            val locations = getEntries<FirestoreLocation>(firestoreLocation)
            val sublocations = getEntries<FirestoreCommonEntry>(firestoreSublocation)
            val categories = getEntries<FirestoreCategory>(firestoreCategory)
            val subcategories = getEntries<FirestoreSubcategory>(firestoreSubcategory)
            val items = getEntries<FirestoreItem>(firestoreItems)
            val ownershipTypes = getEntries<FirestoreCommonEntry>(firestoreOwnership)
            val users = getEntries<FirestoreCommonEntry>(firestoreAdminUsers)
            val adminUsers = getEntries<FirestoreCommonEntry>(firestoreUsers)
            val duty = firestoreDuty?.let { getEntries<FirestoreDuty>(it) }
            val unit = firestoreUnits?.let { getEntries<FirestoreCommonEntry>(it) }
            val deliveryNotes = firestoreDeliveryNote.let { getEntries<FirestoreDeliveryNote>(it)}
//
            val backupObject = FirestoreBackupObject(
                issuance = issuances,
                locations = locations,
                sublocations = sublocations,
                categories = categories,
                subcategories = subcategories,
                items = items,
                ownershipTypes = ownershipTypes,
                users = users,
                duty = duty,
                unit,
                adminUsers = adminUsers,
                deliveryNote = deliveryNotes
            )
            val root = File(filesDir, "export")
            if (!root.exists()) root.mkdirs()
            val key = key(Date(), "backup_${Date().time.timestampToFormattedDate24h()}")
            val filePath = File(root, "$key.json")
            val jsonString = GsonBuilder().setPrettyPrinting().create().toJson(backupObject)
            FileWriter(filePath, false).use { writer ->
                writer.append(jsonString)
                writer.flush()
                writer.close()
            }
            val storageRef: StorageReference = Firebase.storage.reference
            val ref = storageRef.child("backups/$key.json")
            val uploadTask = ref.putFile(filePath.toUri())
            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }.await()
            getBackupsList()
        } catch (e: Exception) {
            return TaskError<Any, LSError>(SimpleError("backup failed due to ${e.message}"))
        }
        return TaskSuccess()
    }

    override suspend fun getBackupsList() = try {
        TaskSuccess<List<String>, LSError>(Firebase.storage.reference.child("backups/").listAll()
            .await().items.map { it.name })
    } catch (e: Exception) {
        TaskError(SimpleError(e.message))
    }

    override suspend fun restore(file: String) = try {
        val storage = Firebase.storage.reference.child("backups/$file")
        val root = File(filesDir, "export")
        if (!root.exists()) root.mkdirs()
        val filePath = File(root, file)
        storage.getFile(filePath).await()
        val gson = Gson()
        val inputString = File(filePath.path).bufferedReader().readText()
        val backupObject = gson.fromJson(inputString, FirestoreBackupObject::class.java)
        val batch = db.batch()
        backupObject.locations.forEach {
            batch.set(firestoreLocation.document(it.first), it.second)
        }
        backupObject.sublocations.forEach {
            batch.set(firestoreSublocation.document(it.first), it.second)
        }
        backupObject.categories.forEach {
            batch.set(firestoreCategory.document(it.first), it.second)
        }
        backupObject.subcategories.forEach {
            batch.set(firestoreSubcategory.document(it.first), it.second)
        }
        firestoreUnits?.let { collection ->
            backupObject.responsibleUnits?.forEach {
                batch.set(collection.document(it.first), it.second)
            }
        }
        backupObject.issuance.forEach { batch.set(firestoreIssuance.document(it.first), it.second) }
        backupObject.items.forEach { batch.set(firestoreItems.document(it.first), it.second) }
        backupObject.ownershipTypes.forEach {
            batch.set(firestoreOwnership.document(it.first), it.second)
        }
        firestoreDuty?.let { collection ->
            backupObject.duty?.forEach { batch.set(collection.document(it.first), it.second) }
        }
        backupObject.users.forEach { batch.set(firestoreUsers.document(it.first), it.second) }
        backupObject.adminUsers.forEach {
            batch.set(
                firestoreAdminUsers.document(it.first), it.second
            )
        }
        backupObject.deliveryNote.forEach {
            batch.set(firestoreDeliveryNote.document(it.first), it.second)
        }
        batch.commit().await()
        TaskSuccess<Any, LSError>()
    } catch (e: Exception) {
        TaskError(SimpleError(e.message))
    }

    private suspend fun editIssuance(item: FirestoreIssuance, firestoreId: String) {
        log("editItem(...): item=$item")
        val date = SimpleDateFormat(TIME_FORMAT).parse(item.date) ?: return
        firestoreIssuance.document(firestoreId).update(
            hashMapOf("date_timestamp" to Timestamp(date)) as Map<String, Any>
        ).await()
    }
}
