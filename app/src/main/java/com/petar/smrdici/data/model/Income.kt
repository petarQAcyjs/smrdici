package com.petar.smrdici.data.model

import java.util.Date

data class Income(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val date: String = "", // Format "YYYY-MM-DD"
    val accountId: String = ""
) {
    // Pomoćna metoda za konverziju u Date objekat kada je potrebno
    fun getDateObject(): Date? {
        return try {
            if (date.isEmpty()) return Date()
            
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            dateFormat.parse(date)
        } catch (e: Exception) {
            Date()
        }
    }
    
    // Pomoćna metoda za formatiranje datuma u lokalni format
    fun getFormattedDate(): String {
        return try {
            if (date.isEmpty()) return ""
            
            // Prvo parsiramo originalni string
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            
            // Zatim formatiramo u lokalni format
            val outputFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            val parsedDate = inputFormat.parse(date) ?: return ""
            outputFormat.format(parsedDate)
        } catch (e: Exception) {
            ""
        }
    }
} 