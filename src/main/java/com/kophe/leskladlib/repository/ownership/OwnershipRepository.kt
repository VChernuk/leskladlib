package com.kophe.leskladlib.repository.ownership

import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.OwnershipType
import com.kophe.leskladlib.repository.common.TaskResult

interface OwnershipRepository {

    suspend fun precacheValues()

    suspend fun allOwnershipTypes(forceReload: Boolean = false): TaskResult<List<OwnershipType>, LSError>

    suspend fun getOwnershipType(id: String): OwnershipType?
}
