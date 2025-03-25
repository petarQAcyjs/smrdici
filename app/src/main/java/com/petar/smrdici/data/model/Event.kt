package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Event(
    var id: String? = null,
    val title: String = "",
    val description: String? = null,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val allDay: Boolean = false,
    val location: String = "",
    val color: String = "#4285F4", // Подразумевана плава боја
    val createdBy: String = "",
    val familyId: String = "",
    val participants: List<String> = emptyList(),
    val calendarId: String? = null,
    val createdAt: Timestamp? = null,
    val assignee: String = "Сви"
) {
    fun toDate() = startTime?.toDate()
}

enum class EventColor(val colorHex: String, val displayName: String) {
    BLUE("#4285F4", "Плава"),
    RED("#EA4335", "Црвена"),
    GREEN("#34A853", "Зелена"),
    YELLOW("#FBBC05", "Жута"),
    PURPLE("#9C27B0", "Љубичаста"),
    TEAL("#009688", "Тиркизна")
}

enum class EventAssignee(val displayName: String, val initial: String, val color: String) {
    EVERYONE("Сви", "С", "#4285F4"),
    PETAR("Петар", "П", "#3F51B5"),
    NATASA("Наташа", "Н", "#DB4437"),
    MILICA("Милица", "М", "#E91E63"),
    BOGDAN("Богдан", "Б", "#F4B400")
}