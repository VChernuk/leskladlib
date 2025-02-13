package com.kophe.leskladlib.repository.locations

import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.Location
import com.kophe.leskladlib.repository.common.Sublocation
import com.kophe.leskladlib.repository.common.TaskResult

interface LocationsRepository {

    suspend fun precacheValues()

    suspend fun allLocations(forceReload: Boolean = false): TaskResult<List<Location>, LSError>

    suspend fun getLocation(id: String): Location?

    suspend fun getSublocation(id: String): Sublocation?

    suspend fun allSublocations(): TaskResult<List<Sublocation>, LSError>

    suspend fun updateLocation(location: Location): TaskResult<Any, LSError>

}
