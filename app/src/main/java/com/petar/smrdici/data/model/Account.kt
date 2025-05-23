package com.petar.smrdici.data.model

import com.google.firebase.firestore.PropertyName
import java.util.UUID

data class Account(
    @get:PropertyName("id") @PropertyName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @get:PropertyName("userId") @PropertyName("userId")
    val userId: String = "",
    
    @get:PropertyName("name") @PropertyName("name")
    val name: String = "",
    
    @get:PropertyName("balance") @PropertyName("balance")
    val balance: Double = 0.0,
    
    @get:PropertyName("currency") @PropertyName("currency")
    val currency: String = DEFAULT_CURRENCY,
    
    @get:PropertyName("color") @PropertyName("color")
    val color: Int = 0,
    
    @get:PropertyName("isDefault") @PropertyName("isDefault")
    val isDefault: Boolean = false,
    
    @get:PropertyName("type") @PropertyName("type")
    val type: AccountType = AccountType.CASH,
    
    @get:PropertyName("isActive") @PropertyName("isActive")
    val isActive: Boolean = true,
    
    @get:PropertyName("createdAt") @PropertyName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @get:PropertyName("updatedAt") @PropertyName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Required no-argument constructor for Firestore
    constructor() : this(
        id = UUID.randomUUID().toString(),
        userId = "",
        name = "",
        balance = 0.0,
        currency = DEFAULT_CURRENCY,
        color = 0,
        isDefault = false,
        type = AccountType.CASH,
        isActive = true,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

enum class AccountType {
    CASH,
    BANK,
    CREDIT_CARD,
    SAVINGS,
    INVESTMENT,
    OTHER
}

// Овај модел се више не користи јер смо уклонили рачуне из трансакција
// Можемо га потпуно уклонити 