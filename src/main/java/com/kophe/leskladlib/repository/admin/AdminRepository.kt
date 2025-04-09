package com.kophe.leskladlib.repository.admin

import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.TaskResult

interface AdminRepository {

    suspend fun setAllOwnerTypesToUnknown(): TaskResult<Any, LSError>

    suspend fun migrateIssuanceToTimestamp(): TaskResult<Any, LSError>

    suspend fun migrateDeliveryNoteToTimestamp(): TaskResult<Any, LSError>

    suspend fun createBackupFile(): TaskResult<Any, LSError>

    suspend fun getBackupsList(): TaskResult<List<String>, LSError>

    suspend fun restore(file: String): TaskResult<Any, LSError>

}
