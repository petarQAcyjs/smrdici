package com.petar.smrdici.data.model

import androidx.annotation.Keep

@Keep
data class Account(
    @field:JvmField val id: String = "",
    @field:JvmField val name: String = "",
    @field:JvmField val balance: Double = 0.0,
    @field:JvmField val currency: String = "RSD",
    @field:JvmField val color: Int = 0,
    @field:JvmField val isDefault: Boolean = false,
    @field:JvmField val type: AccountType = AccountType.CASH
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