package com.kophe.leskladlib.repository.issuance

import com.kophe.leskladlib.repository.common.Issuance
import com.kophe.leskladlib.repository.common.Item
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.ResponsibleUnit
import com.kophe.leskladlib.repository.common.Sublocation
import com.kophe.leskladlib.repository.common.TaskResult

interface IssuanceRepository {

    suspend fun findIssuanceById(id: String?): TaskResult<Issuance, LSError>

    suspend fun issuanceList(forceRefresh: Boolean = true): TaskResult<List<Issuance>, LSError>

    suspend fun createIssuance(
        items: List<Item>,
        issuanceInfoContainer: IssuanceInfoContainer,
        forceDivideQuantityItems: Boolean = false
    ): TaskResult<*, LSError>

}

data class IssuanceInfoContainer(
    val from: String,
    val receiver: String,
    val location: Location,
    val sublocation: Sublocation?,
    val responsibleUnit: ResponsibleUnit?,
    val notes: String
)
