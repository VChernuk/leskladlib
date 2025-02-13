package com.kophe.leskladlib.repository.units

import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.ResponsibleUnit
import com.kophe.leskladlib.repository.common.TaskResult

interface UnitsRepository {

    suspend fun precacheValues()

    suspend fun allUnits(forceReload: Boolean = false): TaskResult<List<ResponsibleUnit>, LSError>

    suspend fun getUnit(id: String?): ResponsibleUnit?

}
