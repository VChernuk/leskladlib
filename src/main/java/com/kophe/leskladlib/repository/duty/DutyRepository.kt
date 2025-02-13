package com.kophe.leskladlib.repository.duty

import com.kophe.leskladlib.repository.common.*

interface DutyRepository {

    suspend fun dutyList(): TaskResult<List<Duty>, LSError>

    suspend fun createDuty(
        user: String,//TODO: move to repo
        sublocation: Sublocation,
        approvedItems: List<Item>,
        declinedItems: List<Item>
    ): TaskResult<*, LSError>

}
