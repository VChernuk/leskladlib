package com.kophe.leskladlib.repository.images

import android.net.Uri
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.kophe.leskladlib.TIME_FORMAT
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.repository.common.BaseRepository
import com.kophe.leskladlib.repository.common.ItemImage
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.TaskResult
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class DefaultImagesRepository(loggingUtil: LoggingUtil) : ImagesRepository,
    BaseRepository(loggingUtil) {

    override suspend fun uploadImage(path: Uri) = try {
        TaskResult.TaskSuccess<Uri, LSError>()
        log("uploadImage(...): path=$path")
        val storageRef: StorageReference = Firebase.storage.reference
        val now = Date()//TODO: move from here
        val ref = storageRef.child(
            "images/${
                SimpleDateFormat("dd_MM_yyyy_HH_mm_ss").format(now)
            }.jpg"
        )
        val uploadTask = ref.putFile(path)
        val result = uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) task.exception?.let { throw it }
            ref.downloadUrl
        }.await()
        val stringResult = "${result.scheme}://${result.authority}${result.path}"
        TaskResult.TaskSuccess(ItemImage(stringResult, TIME_FORMAT.format(Date())))
    } catch (e: Exception) {
        TaskResult.TaskError<ItemImage, LSError>(LSError.SimpleError(e.message))
    }

}
