package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Transaction(
    @DocumentId val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val date: Timestamp = Timestamp.now(),
    val createdBy: String = "",
    val familyId: String = ""
)

enum class TransactionType {
    INCOME, EXPENSE
}

enum class TransactionCategory(val displayName: String) {
    // Приходи
    SALARY("Плата"),
    BONUS("Бонус"),
    GIFT("Поклон"),
    OTHER_INCOME("Остали приходи"),
    
    // Расходи
    FOOD("Храна"),
    UTILITIES("Комуналије"),
    RENT("Станарина"),
    TRANSPORTATION("Превоз"),
    ENTERTAINMENT("Забава"),
    HEALTH("Здравље"),
    EDUCATION("Образовање"),
    SHOPPING("Куповина"),
    OTHER_EXPENSE("Остали расходи")
} 