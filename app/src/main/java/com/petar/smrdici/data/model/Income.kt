package com.petar.smrdici.data.model

import com.google.firebase.Timestamp
import java.util.Date

data class Income(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val category: String = "",
    val date: Timestamp = Timestamp(Date()),
    val accountId: String = ""
) 