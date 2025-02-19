package com.kophe.leskladlib.repository.common

data class RepositoryBuilder(
    val categoriesCollection: String,
    val ownershipTypesCollection: String,
    val locationsCollection: String,
    val subcategoriesCollection: String,
    val itemsCollection: String,
    val sublocations: String,
    val issuanceCollection: String,
    val dutyCollection: String,
    val usersCollection: String,
    val adminsCollection: String,
    val unitsCollection: String,
    val deliveryNotesCollection: String
)
