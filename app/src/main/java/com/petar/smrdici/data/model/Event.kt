package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Event(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp? = null,
    val allDay: Boolean = false,
    val location: String = "",
    val color: String = "#4285F4", // Подразумевана плава боја
    val createdBy: String = "",
    val familyId: String = "",
    val participants: List<String> = emptyList()
)

enum class EventColor(val colorHex: String, val displayName: String) {
    BLUE("#4285F4", "Плава"),
    RED("#EA4335", "Црвена"),
    GREEN("#34A853", "Зелена"),
    YELLOW("#FBBC05", "Жута"),
    PURPLE("#9C27B0", "Љубичаста"),
    TEAL("#009688", "Тиркизна")
}