package com.petar.smrdici.data.model

import java.util.UUID

sealed class Transaction {
    abstract val id: String
    abstract val userId: String
    abstract val amount: Double
    abstract val date: String
    abstract val accountId: String
    abstract val description: String
    abstract val category: String
    abstract val createdAt: Long
    abstract val updatedAt: Long

}

data class Expense(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String,
    override val amount: Double,
    override val date: String,
    override val accountId: String,
    override val description: String,
    override val category: String,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis()
) : Transaction()

data class Income(
    override val id: String = UUID.randomUUID().toString(),
    override val userId: String,
    override val amount: Double,
    override val date: String,
    override val accountId: String,
    override val description: String,
    override val category: String,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis()
) : Transaction() 