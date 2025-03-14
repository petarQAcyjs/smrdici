package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ShoppingList(
    var id: String? = null,
    val title: String = "",
    val createdBy: String = "",
    val familyId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val isCompleted: Boolean = false,
    val items: List<ShoppingItem> = emptyList()
)

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val isCompleted: Boolean = false,
    val quantity: Int = 1,
    val note: String = ""
) 