package com.petar.smrdici.data.model

import java.util.UUID

data class Account(
    val id: String = "",
    val name: String = "",
    val balance: Double = 0.0,
    val currency: String = "RSD",
    val color: Int = 0,
    val isDefault: Boolean = false,
    val type: AccountType = AccountType.CASH
)

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