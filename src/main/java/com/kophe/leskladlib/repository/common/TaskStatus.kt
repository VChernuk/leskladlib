package com.kophe.leskladlib.repository.common

sealed class TaskStatus {

    object StatusInProgress : TaskStatus()
    object StatusFinished : TaskStatus()
    class StatusFailed(val error: LSError? = null) : TaskStatus()

}
