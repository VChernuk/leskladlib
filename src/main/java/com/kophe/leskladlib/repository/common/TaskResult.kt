package com.kophe.leskladlib.repository.common

sealed class TaskResult<out T, out R : LSError> {

    class TaskSuccess<T, R : LSError>(val result: T? = null) : TaskResult<T, R>()
    class TaskError<T, R : LSError>(val error: R? = null) : TaskResult<T, R>()

}
