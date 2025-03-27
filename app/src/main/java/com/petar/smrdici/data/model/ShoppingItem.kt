data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val isCompleted: Boolean = false,
    val listId: String = "",
    val createdAt: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val updatedAt: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
) 