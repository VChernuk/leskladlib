package com.kophe.leskladlib.repository.categories

import com.kophe.leskladlib.repository.common.Category
import com.kophe.leskladlib.repository.common.LSError
import com.kophe.leskladlib.repository.common.TaskResult

interface CategoriesRepository {

    suspend fun precacheValues()

    suspend fun allCategories(
        forceReload: Boolean = false,
        includeSets: Boolean = false
    ): TaskResult<List<Category>, LSError>

    suspend fun getCategory(id: String): Category?

    suspend fun updateCategory(category: Category): TaskResult<Any, LSError>
}
