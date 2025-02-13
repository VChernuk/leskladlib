package com.kophe.leskladlib.repository.images

import android.net.Uri
import com.kophe.leskladlib.repository.common.ItemImage
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.TaskResult

interface ImagesRepository {

    suspend fun uploadImage(path: Uri): TaskResult<ItemImage, LSError>

}
