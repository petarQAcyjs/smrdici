package com.petar.smrdici.data.model

import java.util.UUID
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

    // Common functionality for all transactions
    fun getDateObject(): Date? {
        return try {
            if (date.isEmpty()) return Date()
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            dateFormat.parse(date)
        } catch (e: Exception) {
            Date()
        }
    }
    
    fun getFormattedDate(): String {
        return try {
            if (date.isEmpty()) return ""
            
            // First parse the original string
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            
            // Then format to local format
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val parsedDate = inputFormat.parse(date) ?: return ""
            outputFormat.format(parsedDate)
        } catch (e: Exception) {
            ""
        }
    }
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