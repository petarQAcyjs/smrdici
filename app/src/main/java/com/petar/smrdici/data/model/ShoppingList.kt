package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ShoppingList(
    @DocumentId val id: String = "",
    val title: String = "",
    val createdBy: String = "",
    val familyId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val items: List<ShoppingItem> = emptyList()
)

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val isCompleted: Boolean = false,
    val addedBy: String = "",
    val addedAt: Timestamp = Timestamp.now()
) 