package com.petar.smrdici.notification

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Service for fetching weather information from WeatherStack API
 */
class WeatherService(private val context: Context) {
    
    companion object {
        private const val TAG = "WeatherService"
        private const val API_BASE_URL = "http://api.weatherstack.com/current"
        
        // Cache expiration time in minutes
        private const val CACHE_EXPIRATION_MINUTES = 30
        
        // Shared preferences key for storing weather cache
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_WEATHER_DATA = "weather_data"
        private const val KEY_WEATHER_TIMESTAMP = "weather_timestamp"
        private const val KEY_WEATHER_LOCATION = "weather_location"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get weather information for a location
     * @param location City name
     * @param apiKey WeatherStack API key
     * @return Weather information or null if not available
     */
    suspend fun getWeatherInfo(location: String, apiKey: String): WeatherInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cachedWeather = getCachedWeather(location)
                if (cachedWeather != null) {
                    return@withContext cachedWeather
                }
                
                val urlString = "$API_BASE_URL?access_key=$apiKey&query=$location&units=m"
                val url = URL(urlString)
                
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val weatherInfo = parseWeatherResponse(response.toString())
                    
                    // Cache the result
                    cacheWeatherInfo(location, weatherInfo)
                    
                    weatherInfo
                } else {
                    Log.e(TAG, "Error fetching weather: HTTP $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather", e)
                null
            }
        }
    }
    
    /**
     * Parse the JSON response from WeatherStack API
     */
    private fun parseWeatherResponse(response: String): WeatherInfo {
        val jsonObject = JSONObject(response)
        
        // Check for error response
        if (jsonObject.has("success") && !jsonObject.getBoolean("success")) {
            val error = jsonObject.getJSONObject("error")
            val errorInfo = error.getString("info")
            throw Exception("API Error: $errorInfo")
        }
        
        val current = jsonObject.getJSONObject("current")
        val temp = current.getDouble("temperature")
        val humidity = current.getInt("humidity")
        val description = current.getJSONArray("weather_descriptions").getString(0)
        val windSpeed = current.getDouble("wind_speed")
        val iconUrl = current.getJSONArray("weather_icons").getString(0)
        
        // Extract icon code from the URL
        val iconCode = iconUrl.substringAfterLast("/").substringBefore(".")
        
        return WeatherInfo(
            temperature = temp,
            description = description,
            humidity = humidity,
            windSpeed = windSpeed,
            iconCode = iconCode
        )
    }
    
    /**
     * Get cached weather information if available and not expired
     */
    private fun getCachedWeather(location: String): WeatherInfo? {
        val cachedLocation = prefs.getString(KEY_WEATHER_LOCATION, null)
        if (cachedLocation != location) {
            return null
        }
        
        val timestamp = prefs.getLong(KEY_WEATHER_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        
        // Check if cache is expired
        if (currentTime - timestamp > TimeUnit.MINUTES.toMillis(CACHE_EXPIRATION_MINUTES.toLong())) {
            return null
        }
        
        val weatherJson = prefs.getString(KEY_WEATHER_DATA, null) ?: return null
        
        return try {
            val json = JSONObject(weatherJson)
            WeatherInfo(
                temperature = json.getDouble("temperature"),
                description = json.getString("description"),
                humidity = json.getInt("humidity"),
                windSpeed = json.getDouble("windSpeed"),
                iconCode = json.getString("iconCode")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cached weather data", e)
            null
        }
    }
    
    /**
     * Cache weather information
     */
    private fun cacheWeatherInfo(location: String, weatherInfo: WeatherInfo) {
        val json = JSONObject().apply {
            put("temperature", weatherInfo.temperature)
            put("description", weatherInfo.description)
            put("humidity", weatherInfo.humidity)
            put("windSpeed", weatherInfo.windSpeed)
            put("iconCode", weatherInfo.iconCode)
        }
        
        prefs.edit().apply {
            putString(KEY_WEATHER_DATA, json.toString())
            putLong(KEY_WEATHER_TIMESTAMP, System.currentTimeMillis())
            putString(KEY_WEATHER_LOCATION, location)
            apply()
        }
    }
    
    /**
     * Get weather advice based on weather conditions
     */
    fun getWeatherAdvice(weatherInfo: WeatherInfo): String {
        val advice = StringBuilder()
        
        // Primary weather condition advice
        val primaryAdvice = when {
            weatherInfo.description.contains("rain", ignoreCase = true) -> 
                "🌧️ Не може Мика у парк. Рекоше падаће киша."
            weatherInfo.description.contains("snow", ignoreCase = true) -> 
                "❄️ Рекоше да ће снијег, нек се Смрдићи обуку топло."
            weatherInfo.description.contains("thunderstorm", ignoreCase = true) || 
            weatherInfo.description.contains("thunder", ignoreCase = true) -> 
                "⛈️ Зачепите ђеци уши! Грми."
            weatherInfo.description.contains("fog", ignoreCase = true) ||
            weatherInfo.description.contains("mist", ignoreCase = true) ->
                "🌫️ Закачите ђецу на поводац! Видљивост је смањена због магле."
            weatherInfo.description.contains("cloud", ignoreCase = true) -> 
                "☁️ Сенка рече биће облачно ал неће падат киша."
            weatherInfo.description.contains("clear", ignoreCase = true) || 
            weatherInfo.description.contains("sunny", ignoreCase = true) -> 
                "☀️ Могу ђеца у парк ако стигне неко да их изведе."
            else -> "🌤️ Тренутни услови: ${weatherInfo.description}"
        }
        
        advice.append(primaryAdvice)
        
        // Temperature advice
        val tempAdvice = when {
            weatherInfo.temperature > 35 -> 
                "\n🔥 Пали климу, напољу је пакао."
            weatherInfo.temperature > 30 -> 
                "\n🌡️ Преко 30 су најавили, покуваћемо се."
            weatherInfo.temperature > 25 -> 
                "\n☀️ Најјаче вријеме ѕа Мику и Богија."
            weatherInfo.temperature > 15 -> 
                "\n🌤️ Ммммм, таман."
            weatherInfo.temperature > 5 -> 
                "\n🧥 Смрзнуће се шарена гузица."
            weatherInfo.temperature > 0 -> 
                "\n❄️ Хладно је! Само да не заслине."
            weatherInfo.temperature > -10 -> 
                "\n🧤 Лол најавили минус."
            else -> 
                "\n🥶 Екстремно хладно! Избегавајте дуготрајно излагање хладноћи."
        }
        
        advice.append(tempAdvice)
        
        // Wind advice
        if (weatherInfo.windSpeed > 20) {
            advice.append("\n💨 Јаки ветрови! Натрпај ђеци камење у џепове.")
        } else if (weatherInfo.windSpeed > 10) {
            advice.append("\n🌬️ Ветровито је! Купите папирне марамице, Смрдићима ће нос да цури.")
        }
        
        // Humidity advice
        if (weatherInfo.humidity > 80) {
            advice.append("\n💧 Није што је вруће, него што је нека спарина.")
        } else if (weatherInfo.humidity < 30) {
            advice.append("\n🏜️ Ниска влажност. Побољшајте хидртацију коже.")
        }
        
        // Additional comfort advice
        val comfortAdvice = when {
            weatherInfo.temperature > 25 && weatherInfo.humidity > 70 -> 
                "\n💦 Није што је вруће, него што је нека спарина."
            weatherInfo.temperature < 5 && weatherInfo.windSpeed > 10 -> 
                "\n🥶 Хладно и ветровито. Обуците се веома топло!"
            weatherInfo.temperature > 20 && weatherInfo.temperature < 25 && weatherInfo.humidity < 60 -> 
                "\n😊 То је то! Изводимо ђецу напоље!"
            else -> ""
        }
        
        advice.append(comfortAdvice)
        
        return advice.toString()
    }
}

/**
 * Weather information data class
 */
data class WeatherInfo(
    val temperature: Double,
    val description: String,
    val humidity: Int,
    val windSpeed: Double,
    val iconCode: String
) 