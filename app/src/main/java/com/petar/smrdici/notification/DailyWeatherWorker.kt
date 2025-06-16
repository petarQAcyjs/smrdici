package com.petar.smrdici.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.petar.smrdici.SmrdiciApplication
import com.petar.smrdici.data.model.Event
import com.petar.smrdici.data.repository.EventRepository
import com.petar.smrdici.util.ApiKeys
import java.util.Calendar
import java.util.Date

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
                
                // Create a more engaging notification title
                val notificationTitle = when {
                    weatherInfo.description.contains("rain", ignoreCase = true) -> "🌧️ Киша у $location"
                    weatherInfo.description.contains("snow", ignoreCase = true) -> "❄️ Снег у $location"
                    weatherInfo.description.contains("thunderstorm", ignoreCase = true) -> "⛈️ Грмљавина у $location"
                    weatherInfo.description.contains("sunny", ignoreCase = true) -> "☀️ Сунчано у $location"
                    weatherInfo.description.contains("cloud", ignoreCase = true) -> "☁️ Облачно у $location"
                    else -> "🌤️ Временска прогноза за $location"
                }
                
                // Create detailed notification content
                val notificationContent = buildString {
                    append("📊 Тренутни услови:\n")
                    append("🌡️ Температура: ${weatherInfo.temperature}°C\n")
                    append("💨 Ветар: ${weatherInfo.windSpeed} km/h\n")
                    append("💧 Влажност: ${weatherInfo.humidity}%\n")
                    append("☁️ Стање: $translatedDescription\n\n")
                    append(weatherAdvice)
                    
                    // Add today's events if any
                    val todayEvents = getTodayEvents()
                    if (todayEvents.isNotEmpty()) {
                        append("\n📅 Данашњи догађаји:\n")
                        todayEvents.forEach { event ->
                            val time = formatEventTime(event)
                            append("• $time - ${event.title}\n")
                        }
                    } else {
                        append("\n📅 Данас нема заказаних догађаја")
                    }
                }
                
                notificationService.showDailyWeatherNotification(notificationTitle, notificationContent)
                
                return Result.success()
            } else {
                Log.e(TAG, "Failed to fetch weather info")
                
                // Show a test notification even when weather data fetch fails
                val notificationService = NotificationService(context)
                
                // Get today's events even if weather fails
                val todayEvents = getTodayEvents()
                val eventsContent = if (todayEvents.isNotEmpty()) {
                    buildString {
                        append("\n📅 Данашњи догађаји:\n")
                        todayEvents.forEach { event ->
                            val time = formatEventTime(event)
                            append("• $time - ${event.title}\n")
                        }
                    }
                } else {
                    "\n📅 Данас нема заказаних догађаја"
                }
                
                notificationService.showDailyWeatherNotification(
                    "🌤️ Временска прогноза",
                    "📊 Тренутни подаци о времену нису доступни.\n\n" +
                    "🔧 Могући узроци:\n" +
                    "• Проверите интернет везу\n" +
                    "• Проверите API кључ за временску прогнозу\n" +
                    "• Покушајте поново касније\n\n" +
                    "💡 Обавештења о времену ће се поново активирати када се подаци поново постану доступни." +
                    eventsContent
                )
                
                // Return success instead of retry
                return Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in daily weather worker", e)
            
            // Show a test notification even when an error occurs
            val notificationService = NotificationService(context)
            
            // Get today's events even if weather fails
            val todayEvents = getTodayEvents()
            val eventsContent = if (todayEvents.isNotEmpty()) {
                buildString {
                    append("\n📅 Данашњи догађаји:\n")
                    todayEvents.forEach { event ->
                        val time = formatEventTime(event)
                        append("• $time - ${event.title}\n")
                    }
                }
            } else {
                "\n📅 Данас нема заказаних догађаја"
            }
            
            notificationService.showDailyWeatherNotification(
                "⚠️ Грешка у временској прогнози",
                "📊 Неуспешно учитавање временских података.\n\n" +
                "🔧 Детаљи грешке:\n" +
                "• ${e.message}\n\n" +
                "💡 Покушајте поново касније или проверите подешавања апликације." +
                eventsContent
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
            description.contains("Moderate rain", ignoreCase = true) -> "Умерена киша"
            description.contains("Heavy rain", ignoreCase = true) -> "Јака киша"
            description.contains("Rain", ignoreCase = true) -> "Киша"
            description.contains("Thunderstorm", ignoreCase = true) -> "Грмљавина"
            description.contains("Thunder", ignoreCase = true) -> "Грмљавина"
            description.contains("Light snow", ignoreCase = true) -> "Слаб снег"
            description.contains("Moderate snow", ignoreCase = true) -> "Умерен снег"
            description.contains("Heavy snow", ignoreCase = true) -> "Јак снег"
            description.contains("Snow", ignoreCase = true) -> "Снег"
            description.contains("Sleet", ignoreCase = true) -> "Суснежица"
            description.contains("Freezing", ignoreCase = true) -> "Ледено"
            description.contains("Drizzle", ignoreCase = true) -> "Росуља"
            description.contains("Hail", ignoreCase = true) -> "Град"
            description.contains("Shower", ignoreCase = true) -> "Пљусак"
            description.contains("Blizzard", ignoreCase = true) -> "Мећава"
            description.contains("Storm", ignoreCase = true) -> "Олуја"
            description.contains("Windy", ignoreCase = true) -> "Ветровито"
            description.contains("Breezy", ignoreCase = true) -> "Поветарац"
            description.contains("Calm", ignoreCase = true) -> "Безветрено"
            description.contains("Hot", ignoreCase = true) -> "Вруће"
            description.contains("Cold", ignoreCase = true) -> "Хладно"
            description.contains("Mild", ignoreCase = true) -> "Благо"
            description.contains("Warm", ignoreCase = true) -> "Топло"
            description.contains("Cool", ignoreCase = true) -> "Хладно"
            description.contains("Humid", ignoreCase = true) -> "Влажно"
            description.contains("Dry", ignoreCase = true) -> "Суво"
            description.contains("Hazy", ignoreCase = true) -> "Магловито"
            description.contains("Smoky", ignoreCase = true) -> "Димасто"
            description.contains("Dusty", ignoreCase = true) -> "Прашњаво"
            description.contains("Sandstorm", ignoreCase = true) -> "Песак"
            description.contains("Tornado", ignoreCase = true) -> "Торнадо"
            description.contains("Hurricane", ignoreCase = true) -> "Ураган"
            description.contains("Cyclone", ignoreCase = true) -> "Циклон"
            description.contains("Typhoon", ignoreCase = true) -> "Тајфун"
            else -> description // Return original if no translation found
        }
    }
    
    /**
     * Get today's events from the repository
     */
    private suspend fun getTodayEvents(): List<Event> {
        return try {
            val repository = EventRepository(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance(),
                context
            )
            
            // Get start and end of today
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.time
            
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfDay = calendar.time
            
            val result = repository.getEventsSync(startOfDay, endOfDay)
            if (result.isSuccess) {
                result.getOrNull() ?: emptyList()
            } else {
                Log.e(TAG, "Failed to get today's events", result.exceptionOrNull())
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's events", e)
            emptyList()
        }
    }
    
    /**
     * Format event time for display
     */
    private fun formatEventTime(event: Event): String {
        return if (event.allDay) {
            "Целодневно"
        } else {
            event.startTime?.toDate()?.let { date ->
                val calendar = Calendar.getInstance()
                calendar.time = date
                String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
            } ?: "Непознато време"
        }
    }
} 