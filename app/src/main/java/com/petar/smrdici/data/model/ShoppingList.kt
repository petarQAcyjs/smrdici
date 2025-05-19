package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class ShoppingList(
    var id: String? = null,
    val title: String = "",
    val createdBy: String = "",
    val familyId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    @get:PropertyName("completed")
    @set:PropertyName("completed")
    @PropertyName("completed")
    var isCompleted: Boolean = false,
    val items: List<ShoppingItem> = emptyList()
)

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    @get:PropertyName("completed")
    @PropertyName("completed")
    val isCompleted: Boolean = false,
    val quantity: Int = 1,
    val note: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val position: Int = -1 // Default to -1 for new items
) 