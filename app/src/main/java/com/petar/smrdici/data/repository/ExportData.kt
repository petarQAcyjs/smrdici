package com.petar.smrdici.data.repository

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Класа која садржи све податке апликације за извоз/увоз
 * (Simplified version - not used for actual export/import anymore)
 */
data class ExportData(
    val accounts: List<Any> = emptyList(),
    val expenses: List<Any> = emptyList(),
    val incomes: List<Any> = emptyList(),
    val settings: Map<String, Any> = emptyMap(),
    val version: Int = 1,
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
) 