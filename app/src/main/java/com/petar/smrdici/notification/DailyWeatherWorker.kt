package com.petar.smrdici.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.util.ApiKeys

/**
 * Worker that fetches weather data and displays a daily weather notification.
 * This is scheduled to run once per day at 8:00 AM.
 */
class DailyWeatherWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DailyWeatherWorker"
        const val WORKER_NAME = "daily_weather_worker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily weather notification work")
        
        // Check if weather-aware notifications are enabled
        val notificationManager = SmrdiciApplication.getNotificationManager()
        if (!notificationManager.weatherAwareEnabled) {
            Log.d(TAG, "Weather-aware notifications are disabled, skipping")
            return Result.success()
        }
        
        try {
            // Default location is Belgrade, Serbia
            val location = inputData.getString("LOCATION") ?: "Београд"
            
            // Fetch weather information
            val weatherService = WeatherService(context)
            val apiKey = ApiKeys.WEATHER_API_KEY
            
            val weatherInfo = weatherService.getWeatherInfo(location, apiKey)
            if (weatherInfo != null) {
                // Get weather advice
                val weatherAdvice = weatherService.getWeatherAdvice(weatherInfo)
                
                // Store in SharedPreferences for future use
                val weatherPrefs = context.getSharedPreferences("weather_info", Context.MODE_PRIVATE)
                weatherPrefs.edit().apply {
                    putString("weather_advice", weatherAdvice)
                    putString("weather_description", weatherInfo.description)
                    putFloat("weather_temperature", weatherInfo.temperature.toFloat())
                    apply()
                }
                
                Log.d(TAG, "Weather info fetched: $weatherAdvice")
                
                // Translate common weather descriptions
                val translatedDescription = translateWeatherDescription(weatherInfo.description)
                
                // Show the weather notification
                val notificationService = NotificationService(context)
                notificationService.showDailyWeatherNotification(
                    "Временска прогноза",
                    "Тренутни услови у $location: ${weatherInfo.temperature}°C, $translatedDescription\n\n$weatherAdvice"
                )
                
                return Result.success()
            } else {
                Log.e(TAG, "Failed to fetch weather info")
                
                // Show a test notification even when weather data fetch fails
                val notificationService = NotificationService(context)
                notificationService.showDailyWeatherNotification(
                    "Тест обавештења о времену",
                    "Ово је тест обавештење.\n\nТренутни подаци о времену нису доступни. Проверите свој API кључ или интернет везу."
                )
                
                // Return success instead of retry
                return Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in daily weather worker", e)
            
            // Show a test notification even when an error occurs
            val notificationService = NotificationService(context)
            notificationService.showDailyWeatherNotification(
                "Тест обавештења о времену",
                "Ово је тест обавештење.\n\nГрешка: ${e.message}"
            )
            
            // Return success instead of retry
            return Result.success()
        }
    }
    
    /**
     * Translate common weather descriptions from English to Serbian Cyrillic
     */
    private fun translateWeatherDescription(description: String): String {
        return when {
            description.contains("Sunny", ignoreCase = true) -> "Сунчано"
            description.contains("Clear", ignoreCase = true) -> "Ведро"
            description.contains("Partly cloudy", ignoreCase = true) -> "Делимично облачно"
            description.contains("Cloudy", ignoreCase = true) -> "Облачно"
            description.contains("Overcast", ignoreCase = true) -> "Тмурно"
            description.contains("Mist", ignoreCase = true) -> "Измаглица"
            description.contains("Fog", ignoreCase = true) -> "Магла"
            description.contains("Light rain", ignoreCase = true) -> "Слаба киша"
            description.contains("Rain", ignoreCase = true) -> "Киша"
            description.contains("Heavy rain", ignoreCase = true) -> "Јака киша"
            description.contains("Thunderstorm", ignoreCase = true) -> "Грмљавина"
            description.contains("Thunder", ignoreCase = true) -> "Грмљавина"
            description.contains("Snow", ignoreCase = true) -> "Снег"
            description.contains("Light snow", ignoreCase = true) -> "Слаб снег"
            description.contains("Heavy snow", ignoreCase = true) -> "Јак снег"
            description.contains("Sleet", ignoreCase = true) -> "Суснежица"
            description.contains("Freezing", ignoreCase = true) -> "Ледено"
            description.contains("Drizzle", ignoreCase = true) -> "Росуља"
            description.contains("Hail", ignoreCase = true) -> "Град"
            description.contains("Shower", ignoreCase = true) -> "Пљусак"
            else -> description // Return original if no translation found
        }
    }
} 