package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Event(
    var id: String? = null,
    val title: String = "",
    val description: String? = null,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val allDay: Boolean = false,
    val location: String = "",
    val color: String = "#4285F4", // Подразумевана плава боја
    val assignee: String = EventAssignee.EVERYONE.name,
    val createdBy: String? = null,
    val familyId: String = "",
    val participants: List<String> = emptyList(),
    val calendarId: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    @get:PropertyName("isRecurringYearly") @set:PropertyName("isRecurringYearly")
    var isRecurringYearly: Boolean = false // Поље за годишње понављање догађаја
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as Event
        
        if (title != other.title) return false
        if (startTime != other.startTime) return false
        if (assignee != other.assignee) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (startTime?.hashCode() ?: 0)
        result = 31 * result + assignee.hashCode()
        return result
    }
}

enum class EventColor(val colorHex: String) {
    BLUE("#4285F4"),
    RED("#EA4335"),
    GREEN("#34A853"),
    YELLOW("#FBBC05"),
    PURPLE("#9C27B0"),
    TEAL("#009688")
}

enum class EventAssignee(val displayName: String, val initial: String, val color: String) {
    EVERYONE("Сви", "С", "#4285F4"),
    PETAR("Петар", "П", "#3F51B5"),
    NATASA("Наташа", "Н", "#DB4437"),
    MILICA("Милица", "М", "#E91E63"),
    BOGDAN("Богдан", "Б", "#F4B400")
}

// Enum for recurring event types
enum class RecurringType(val displayName: String) {
    NONE("Не понавља се"),
    YEARLY("Годишње")
}