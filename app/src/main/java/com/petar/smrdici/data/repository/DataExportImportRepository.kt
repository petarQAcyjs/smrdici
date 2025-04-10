package com.petar.smrdici.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Репозиторијум за извоз и увоз података апликације
 */
class DataExportImportRepository private constructor(private val context: Context) {
    
    private val gson = Gson()
    
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
                    return@withContext gson.fromJson(jsonData, ExportData::class.java)
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при увозу података", e)
            return@withContext null
        }
    }
    
    /**
     * Креира назив фајла за извоз података базиран на тренутном датуму и времену
     * @return Назив фајла у формату "smrdici_backup_YYYY-MM-DD_HH-mm-ss.json"
     */
    fun createExportFilename(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        val current = LocalDateTime.now().format(formatter)
        return "smrdici_backup_$current.json"
    }
    
    companion object {
        private const val TAG = "DataExportImport"
        
        @Volatile
        private var instance: DataExportImportRepository? = null
        
        fun getInstance(context: Context): DataExportImportRepository {
            return instance ?: synchronized(this) {
                instance ?: DataExportImportRepository(context).also { instance = it }
            }
        }
    }
}

/**
 * Класа која садржи све податке апликације за извоз/увоз
 */
data class ExportData(
    val accounts: List<Any> = emptyList(),
    val expenses: List<Any> = emptyList(),
    val incomes: List<Any> = emptyList(),
    val events: List<Any> = emptyList(),
    val lists: List<Any> = emptyList(),
    val settings: Map<String, Any> = emptyMap(),
    val version: Int = 1,
    val exportDate: String = LocalDateTime.now().toString()
) 