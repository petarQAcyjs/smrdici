package com.petar.smrdici.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.petar.smrdici.data.model.Account
import com.petar.smrdici.data.model.AccountType
import com.petar.smrdici.data.model.Expense
import com.petar.smrdici.data.model.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.petar.smrdici.utils.AppGlobals
import com.petar.smrdici.data.model.CategoryManager
import java.util.UUID

/**
 * Репозиторијум за извоз и увоз података апликације
 */
class DataExportImportRepository private constructor(private val context: Context) {

    private val gson = GsonBuilder().create()

    private fun parseDate(dateElement: JsonElement?): String {
        var date = ""
        if (dateElement != null) {
            try {
                if (dateElement.isJsonObject) {
                    // Обрада Timestamp објекта - конвертујемо у стринг формат
                    val dateObj = dateElement.asJsonObject
                    if (dateObj.has("seconds") && dateObj.has("nanoseconds")) {
                        val seconds = dateObj.get("seconds").asLong
                        val nanoseconds = dateObj.get("nanoseconds").asInt
                        val timestamp = Timestamp(seconds, nanoseconds)
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        date = dateFormat.format(timestamp.toDate())
                    }
                } else if (dateElement.isJsonPrimitive) {
                    val primitive = dateElement.asJsonPrimitive
                    if (primitive.isNumber) {
                        // Unix timestamp u milisekundama - konvertujemo u string
                        val dateObj = Date(primitive.asLong)
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        date = dateFormat.format(dateObj)
                    } else if (primitive.isString) {
                        val dateString = primitive.asString

                        // Ako je već u očekivanom formatu, samo ga koristimo
                        if (dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            date = dateString
                        } else {
                            // Pokušavamo da parsiramo datum u različitim formatima
                            try {
                                // Koristimo SimpleDateFormat za parsiranje
                                val dateFormat = when {
                                    // Format sa punim ISO datumom i vremenom
                                    dateString.contains("T") -> {
                                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                    }
                                    // Format sa vremenom
                                    dateString.contains(":") -> {
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                    }
                                    // Neki drugi format datuma
                                    else -> {
                                        // Pokušavamo da prepoznamo format
                                        if (dateString.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
                                            SimpleDateFormat("dd.MM.yyyy", Locale.US)
                                        } else {
                                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        }
                                    }
                                }

                                dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                val parsedDate = dateFormat.parse(dateString)
                                if (parsedDate != null) {
                                    // Konvertujemo natrag u naš standardni format
                                    val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    outputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    date = outputFormat.format(parsedDate)
                                    Log.d("DataExportImportRepository", "Uspešno parsiran datum: $dateString -> $date")
                                } else {
                                    // Vraćamo današnji datum ako ne može da se parsira
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    date = today.format(Date())
                                    Log.e("DataExportImportRepository", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date")
                                }
                            } catch (e: Exception) {
                                // Vraćamo današnji datum u slučaju greške
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                date = today.format(Date())
                                Log.e("DataExportImportRepository", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Vraćamo današnji datum u slučaju greške
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                date = today.format(Date())
                Log.e("DataExportImportRepository", "Грешка при парсирању датума, koristim današnji datum: $date", e)
            }
        } else {
            // Ako nema datuma, koristimo današnji
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            today.timeZone = java.util.TimeZone.getTimeZone("UTC")
            date = today.format(Date())
            Log.d("DataExportImportRepository", "Nema datuma u JSON-u, koristim današnji datum: $date")
        }
        return date
    }
    
    /**
     * Извози податке апликације у JSON формату на одређену локацију
     * @param uri URI локације за чување података
     * @param data Објекат са подацима за извоз
     * @return true ако је извоз успешан, false у супротном
     */
    suspend fun exportData(uri: Uri, data: ExportData): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                    val jsonData = gson.toJson(data)
                    val byteArray = jsonData.toByteArray(Charsets.UTF_8)
                    outputStream.write(byteArray)
                    outputStream.flush()
                    return@withContext true
                }
            }
            return@withContext false
        } catch (e: IOException) {
            Log.e(TAG, "Грешка при извозу података", e)
            return@withContext false
        }
    }
    
    /**
     * Увози податке апликације из JSON фајла
     * @param uri URI локације JSON фајла
     * @return Објекат са подацима ако је увоз успешан, null у супротном
     */
    suspend fun importData(uri: Uri): ExportData? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonData = reader.readText()
                    
                    // Kreiramo poseban Gson sa našim konverterima
                    val gsonBuilder = GsonBuilder()
                    
                    // Dodajemo konverter za Expense objekte
                    gsonBuilder.registerTypeAdapter(Expense::class.java, ExpenseDeserializer())
                    
                    // Dodajemo konverter za Income objekte
                    gsonBuilder.registerTypeAdapter(Income::class.java, IncomeDeserializer())
                    
                    // Dodajemo konverter za Account objekte
                    gsonBuilder.registerTypeAdapter(Account::class.java, AccountDeserializer())
                    
                    // Dodaj strategiju deserijalizacije koja ignoriše nepoznata polja
                    gsonBuilder.setLenient()
                    
                    // Parsiramo JSON ručno da bismo imali potpunu kontrolu
                    val jsonElement = try {
                        // Покушавамо са новијом верзијом методе
                        JsonParser.parseString(jsonData)
                    } catch (_: NoSuchMethodError) {
                        // Fallback за старије верзије Gson библиотеке
                        try {
                            val jsonParserClass = JsonParser::class.java
                            val parseStringMethod = jsonParserClass.getMethod("parseString", String::class.java)
                            parseStringMethod.invoke(null, jsonData) as JsonElement
                        } catch (e2: Exception) {
                            // За још старије верзије или у случају друге грешке
                            Log.e(TAG, "Грешка при парсирању JSON-а: ${e2.message}")
                            throw e2
                        }
                    }
                    val jsonObject = jsonElement.asJsonObject
                    
                    // Pripremamo listu objekata za ExportData
                    val accounts = parseAccounts(jsonObject, gsonBuilder.create())
                    val expenses = parseExpenses(jsonObject, gsonBuilder.create())
                    val incomes = parseIncomes(jsonObject, gsonBuilder.create())
                    
                    // Uzimamo metapodatke
                    var version = 1
                    if (jsonObject.has("version")) {
                        version = jsonObject.get("version").asInt
                    }
                    
                    var exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    if (jsonObject.has("exportDate")) {
                        exportDate = jsonObject.get("exportDate").asString
                    }
                    
                    // Kreiramo ExportData objekat
                    return@withContext ExportData(
                        accounts = accounts,
                        expenses = expenses,
                        incomes = incomes,
                        version = version,
                        exportDate = exportDate
                    )
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при увозу података", e)
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Парсира JSON низ рачуна у објекте Account
     */
    private fun parseAccounts(jsonObject: JsonObject, gson: Gson): List<Account> {
        val accounts = mutableListOf<Account>()
        
        if (jsonObject.has("accounts") && !jsonObject.get("accounts").isJsonNull) {
            val accountsArray = jsonObject.getAsJsonArray("accounts")
            
            accountsArray.forEach { accountElement ->
                try {
                    val account = gson.fromJson(accountElement, Account::class.java)
                    accounts.add(account)
                } catch (e: Exception) {
                    Log.e(TAG, "Грешка при парсирању рачуна: ${e.message}")
                }
            }
        }
        
        return accounts
    }
    
    /**
     * Парсира JSON низ расхода у објекте Expense
     */
    private fun parseExpenses(jsonObject: JsonObject, gson: Gson): List<Expense> {
        val expenses = mutableListOf<Expense>()
        
        if (jsonObject.has("expenses") && !jsonObject.get("expenses").isJsonNull) {
            val expensesArray = jsonObject.getAsJsonArray("expenses")
            
            expensesArray.forEach { expenseElement ->
                try {
                    val expense = gson.fromJson(expenseElement, Expense::class.java)
                    expenses.add(expense)
                } catch (e: Exception) {
                    Log.e(TAG, "Грешка при парсирању расхода: ${e.message}")
                }
            }
        }
        
        return expenses
    }
    
    /**
     * Парсира JSON низ прихода у објекте Income
     */
    private fun parseIncomes(jsonObject: JsonObject, gson: Gson): List<Income> {
        val incomes = mutableListOf<Income>()
        
        if (jsonObject.has("incomes") && !jsonObject.get("incomes").isJsonNull) {
            val incomesArray = jsonObject.getAsJsonArray("incomes")
            
            incomesArray.forEach { incomeElement ->
                try {
                    val income = gson.fromJson(incomeElement, Income::class.java)
                    incomes.add(income)
                } catch (e: Exception) {
                    Log.e(TAG, "Грешка при парсирању прихода: ${e.message}")
                }
            }
        }
        
        return incomes
    }
    
    /**
     * Креира назив фајла за извоз података базиран на тренутном датуму и времену
     * @return Назив фајла у формату "smrdici_backup_YYYY-MM-DD_HH-mm-ss.json"
     */
    fun createExportFilename(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val current = formatter.format(Date())
        return "smrdici_backup_$current.json"
    }
    
    companion object {
        private const val TAG = "DataExportImport"
        
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DataExportImportRepository? = null
        
        fun getInstance(context: Context): DataExportImportRepository {
            return instance ?: synchronized(this) {
                instance ?: DataExportImportRepository(context.applicationContext).also { instance = it }
            }
        }

        fun parseDate(dateElement: JsonElement?): String {
            var date = ""
            if (dateElement != null) {
                try {
                    if (dateElement.isJsonObject) {
                        // Обрада Timestamp објекта - конвертујемо у стринг формат
                        val dateObj = dateElement.asJsonObject
                        if (dateObj.has("seconds") && dateObj.has("nanoseconds")) {
                            val seconds = dateObj.get("seconds").asLong
                            val nanoseconds = dateObj.get("nanoseconds").asInt
                            val timestamp = Timestamp(seconds, nanoseconds)
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            date = dateFormat.format(timestamp.toDate())
                        }
                    } else if (dateElement.isJsonPrimitive) {
                        val primitive = dateElement.asJsonPrimitive
                        if (primitive.isNumber) {
                            // Unix timestamp u milisekundama - konvertujemo u string
                            val dateObj = Date(primitive.asLong)
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            date = dateFormat.format(dateObj)
                        } else if (primitive.isString) {
                            val dateString = primitive.asString

                            // Ako je već u očekivanom formatu, samo ga koristimo
                            if (dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                date = dateString
                            } else {
                                // Pokušavamo da parsiramo datum u različitim formatima
                                try {
                                    // Koristimo SimpleDateFormat za parsiranje
                                    val dateFormat = when {
                                        // Format sa punim ISO datumom i vremenom
                                        dateString.contains("T") -> {
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                        }
                                        // Format sa vremenom
                                        dateString.contains(":") -> {
                                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                        }
                                        // Neki drugi format datuma
                                        else -> {
                                            // Pokušavamo da prepoznamo format
                                            if (dateString.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) {
                                                SimpleDateFormat("dd.MM.yyyy", Locale.US)
                                            } else {
                                                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                            }
                                        }
                                    }

                                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    val parsedDate = dateFormat.parse(dateString)
                                    if (parsedDate != null) {
                                        // Konvertujemo natrag u naš standardni format
                                        val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        outputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        date = outputFormat.format(parsedDate)
                                        Log.d("DataExportImportRepository", "Uspešno parsiran datum: $dateString -> $date")
                                    } else {
                                        // Vraćamo današnji datum ako ne može da se parsira
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        date = today.format(Date())
                                        Log.e("DataExportImportRepository", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date")
                                    }
                                } catch (e: Exception) {
                                    // Vraćamo današnji datum u slučaju greške
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    date = today.format(Date())
                                    Log.e("DataExportImportRepository", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Vraćamo današnji datum u slučaju greške
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    date = today.format(Date())
                    Log.e("DataExportImportRepository", "Грешка при парсирању датума, koristim današnji datum: $date", e)
                }
            } else {
                // Ako nema datuma, koristimo današnji
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                date = today.format(Date())
                Log.d("DataExportImportRepository", "Nema datuma u JSON-u, koristim današnji datum: $date")
            }
            return date
        }
    }
}

/**
 * Deserijalizator za Expense objekte
 */
class ExpenseDeserializer : JsonDeserializer<Expense> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Expense {
        val jsonObject = json.asJsonObject
        
        // Parse required fields
        val id = jsonObject.get("id")?.asString ?: UUID.randomUUID().toString()
        val userId = jsonObject.get("userId")?.asString ?: ""
        val amount = jsonObject.get("amount")?.asDouble ?: 0.0
        val description = jsonObject.get("description")?.asString ?: ""
        val category = jsonObject.get("category")?.asString ?: ""
        val accountId = jsonObject.get("accountId")?.asString ?: ""
        val date = DataExportImportRepository.parseDate(jsonObject.get("date"))
        val createdAt = jsonObject.get("createdAt")?.asLong ?: System.currentTimeMillis()
        val updatedAt = jsonObject.get("updatedAt")?.asLong ?: System.currentTimeMillis()
        
        return Expense(
            id = id,
            userId = userId,
            amount = amount,
            date = date,
            accountId = accountId,
            description = description,
            category = category,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Deserijalizator za Income objekte
 */
class IncomeDeserializer : JsonDeserializer<Income> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Income {
        val jsonObject = json.asJsonObject
        
        // Parse required fields
        val id = jsonObject.get("id")?.asString ?: UUID.randomUUID().toString()
        val userId = jsonObject.get("userId")?.asString ?: ""
        val amount = jsonObject.get("amount")?.asDouble ?: 0.0
        val description = jsonObject.get("description")?.asString ?: ""
        val category = jsonObject.get("category")?.asString ?: ""
        val accountId = jsonObject.get("accountId")?.asString ?: ""
        val date = DataExportImportRepository.parseDate(jsonObject.get("date"))
        val createdAt = jsonObject.get("createdAt")?.asLong ?: System.currentTimeMillis()
        val updatedAt = jsonObject.get("updatedAt")?.asLong ?: System.currentTimeMillis()
        
        return Income(
            id = id,
            userId = userId,
            amount = amount,
            date = date,
            accountId = accountId,
            description = description,
            category = category,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Deserijalizator za Account objekte
 */
class AccountDeserializer : JsonDeserializer<Account> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Account {
        val jsonObject = json.asJsonObject
        
        // Parse required fields
        val id = jsonObject.get("id")?.asString ?: UUID.randomUUID().toString()
        val userId = jsonObject.get("userId")?.asString ?: ""
        val name = jsonObject.get("name")?.asString ?: ""
        val balance = jsonObject.get("balance")?.asDouble ?: 0.0
        val currency = jsonObject.get("currency")?.asString ?: Account.DEFAULT_CURRENCY
        val color = jsonObject.get("color")?.asInt ?: 0
        val isDefault = jsonObject.get("isDefault")?.asBoolean ?: false
        val type = try {
            AccountType.valueOf(jsonObject.get("type")?.asString ?: AccountType.CASH.name)
        } catch (e: Exception) {
            AccountType.CASH
        }
        val isActive = jsonObject.get("isActive")?.asBoolean ?: true
        val createdAt = jsonObject.get("createdAt")?.asLong ?: System.currentTimeMillis()
        val updatedAt = jsonObject.get("updatedAt")?.asLong ?: System.currentTimeMillis()
        
        return Account(
            id = id,
            userId = userId,
            name = name,
            balance = balance,
            currency = currency,
            color = color,
            isDefault = isDefault,
            type = type,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Класа која садржи све податке апликације за извоз/увоз
 */
data class ExportData(
    val accounts: List<Any> = emptyList(),
    val expenses: List<Any> = emptyList(),
    val incomes: List<Any> = emptyList(),
    val settings: Map<String, Any> = emptyMap(),
    val version: Int = 1,
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)
