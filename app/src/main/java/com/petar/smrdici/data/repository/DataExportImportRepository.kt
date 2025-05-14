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
    }
}

/**
 * Deserijalizator za Expense objekte
 */
class ExpenseDeserializer : JsonDeserializer<Expense> {
    
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
                                    Log.d("ExpenseDeserializer", "Uspešno parsiran datum: $dateString -> $date")
                                } else {
                                    // Vraćamo današnji datum ako ne može da se parsira
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    date = today.format(Date())
                                    Log.e("ExpenseDeserializer", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date")
                                }
                            } catch (e: Exception) {
                                // Vraćamo današnji datum u slučaju greške
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                date = today.format(Date())
                                Log.e("ExpenseDeserializer", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Vraćamo današnji datum u slučaju greške
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                date = today.format(Date())
                Log.e("ExpenseDeserializer", "Грешка при парсирању датума, koristim današnji datum: $date", e)
            }
        } else {
            // Ako nema datuma, koristimo današnji
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            today.timeZone = java.util.TimeZone.getTimeZone("UTC")
            date = today.format(Date())
            Log.d("ExpenseDeserializer", "Nema datuma u JSON-u, koristim današnji datum: $date")
        }
        return date
    }
    
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Expense {
        // Проверавамо да ли је директно послат LinkedTreeMap уместо JsonObject
        if (json is JsonNull) {
            Log.e("ExpenseDeserializer", "JSON елемент је null")
            throw JsonParseException("JSON елемент је null")
        }
        
        val jsonObject = try {
            json.asJsonObject
        } catch (e: Exception) {
            Log.e("ExpenseDeserializer", "Грешка при конверзији JSON елемента у објекат: ${e.message}", e)
            throw e
        }
        
        try {
            // Извлачимо основне податке
            val id = if (jsonObject.has("id")) jsonObject.get("id").asString else ""
            
            // Покушавамо да добијемо износ као број, али можда је у стрингу
            val amount = try {
                if (jsonObject.has("amount")) {
                    if (jsonObject.get("amount").isJsonPrimitive) {
                        val primitive = jsonObject.get("amount").asJsonPrimitive
                        if (primitive.isNumber) {
                            primitive.asDouble
                        } else if (primitive.isString) {
                            primitive.asString.toDoubleOrNull() ?: 0.0
                        } else {
                            0.0
                        }
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }
            } catch (e: Exception) {
                Log.e("ExpenseDeserializer", "Грешка при парсирању износа: ${e.message}")
                0.0
            }
            
            val description = if (jsonObject.has("description")) jsonObject.get("description").asString else ""
            val category = if (jsonObject.has("category")) jsonObject.get("category").asString else ""
            val accountId = if (jsonObject.has("accountId")) jsonObject.get("accountId").asString else ""
            
            // Registrujemo kategoriju ako je nova
            val appContext = AppGlobals.getAppContext()
            if (appContext != null && category.isNotEmpty()) {
                val categoryManager = CategoryManager.getInstance(appContext)
                categoryManager.addExpenseCategory(category)
            }
            
            // Парсирамо датум у формату "YYYY-MM-DD"
            val date = parseDate(if (jsonObject.has("date")) jsonObject.get("date") else null)
            
            Log.d("ExpenseDeserializer", "Успешно десеријализован трошак ID: $id, износ: $amount, датум: $date")
            return Expense(id, amount, description, category, date, accountId)
        } catch (e: Exception) {
            Log.e("ExpenseDeserializer", "Грешка при парсирању објекта трошка: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Deserijalizator za Income objekte
 */
class IncomeDeserializer : JsonDeserializer<Income> {
    
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
                                    Log.d("IncomeDeserializer", "Uspešno parsiran datum: $dateString -> $date")
                                } else {
                                    // Vraćamo današnji datum ako ne može da se parsira
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    date = today.format(Date())
                                    Log.e("IncomeDeserializer", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date")
                                }
                            } catch (e: Exception) {
                                // Vraćamo današnji datum u slučaju greške
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                date = today.format(Date())
                                Log.e("IncomeDeserializer", "Nije moguće parsirati string datuma: $dateString, koristim današnji datum: $date", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Vraćamo današnji datum u slučaju greške
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                today.timeZone = java.util.TimeZone.getTimeZone("UTC")
                date = today.format(Date())
                Log.e("IncomeDeserializer", "Грешка при парсирању датума, koristim današnji datum: $date", e)
            }
        } else {
            // Ako nema datuma, koristimo današnji
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            today.timeZone = java.util.TimeZone.getTimeZone("UTC")
            date = today.format(Date())
            Log.d("IncomeDeserializer", "Nema datuma u JSON-u, koristim današnji datum: $date")
        }
        return date
    }
    
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Income {
        // Проверавамо да ли је директно послат LinkedTreeMap уместо JsonObject
        if (json is JsonNull) {
            Log.e("IncomeDeserializer", "JSON елемент је null")
            throw JsonParseException("JSON елемент је null")
        }
        
        val jsonObject = try {
            json.asJsonObject
        } catch (e: Exception) {
            Log.e("IncomeDeserializer", "Грешка при конверзији JSON елемента у објекат: ${e.message}", e)
            throw e
        }
        
        try {
            // Извлачимо основне податке
            val id = if (jsonObject.has("id")) jsonObject.get("id").asString else ""
            
            // Покушавамо да добијемо износ као број, али можда је у стрингу
            val amount = try {
                if (jsonObject.has("amount")) {
                    if (jsonObject.get("amount").isJsonPrimitive) {
                        val primitive = jsonObject.get("amount").asJsonPrimitive
                        if (primitive.isNumber) {
                            primitive.asDouble
                        } else if (primitive.isString) {
                            primitive.asString.toDoubleOrNull() ?: 0.0
                        } else {
                            0.0
                        }
                    } else {
                        0.0
                    }
                } else {
                    0.0
                }
            } catch (e: Exception) {
                Log.e("IncomeDeserializer", "Грешка при парсирању износа: ${e.message}")
                0.0
            }
            
            val description = if (jsonObject.has("description")) jsonObject.get("description").asString else ""
            val category = if (jsonObject.has("category")) jsonObject.get("category").asString else ""
            val accountId = if (jsonObject.has("accountId")) jsonObject.get("accountId").asString else ""
            
            // Registrujemo kategoriju ako je nova
            val appContext = AppGlobals.getAppContext()
            if (appContext != null && category.isNotEmpty()) {
                val categoryManager = CategoryManager.getInstance(appContext)
                categoryManager.addIncomeCategory(category)
            }
            
            // Парсирамо датум у формату "YYYY-MM-DD"
            val date = parseDate(if (jsonObject.has("date")) jsonObject.get("date") else null)
            
            Log.d("IncomeDeserializer", "Успешно десеријализован приход ID: $id, износ: $amount, датум: $date")
            return Income(id, amount, description, category, date, accountId)
        } catch (e: Exception) {
            Log.e("IncomeDeserializer", "Грешка при парсирању објекта прихода: ${e.message}", e)
            throw e
        }
    }
}

/**
 * Deserijalizator za Account objekte
 */
class AccountDeserializer : JsonDeserializer<Account> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Account {
        val jsonObject = json.asJsonObject
        
        // Izvlačimo sve potrebne podatke za Account
        val id = if (jsonObject.has("id")) jsonObject.get("id").asString else ""
        val name = if (jsonObject.has("name")) jsonObject.get("name").asString else ""
        val balance = if (jsonObject.has("balance")) jsonObject.get("balance").asDouble else 0.0
        val currency = if (jsonObject.has("currency")) jsonObject.get("currency").asString else ""
        val color = if (jsonObject.has("color")) jsonObject.get("color").asInt else 0
        val isDefault = if (jsonObject.has("isDefault")) jsonObject.get("isDefault").asBoolean else false
        
        // Парсирамо AccountType - подразумевана вредност је CASH ако не постоји или је непознат тип
        var accountType = AccountType.CASH
        if (jsonObject.has("type") && !jsonObject.get("type").isJsonNull) {
            try {
                val typeStr = jsonObject.get("type").asString
                accountType = AccountType.valueOf(typeStr)
            } catch (e: Exception) {
                // Ако valueOf не успе, задржавамо подразумевану вредност CASH
                Log.e("AccountDeserializer", "Непознат тип рачуна: ${jsonObject.get("type")}. Грешка: ${e.message}", e)
            }
        }
        
        return Account(id, name, balance, currency, color, isDefault, accountType)
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
