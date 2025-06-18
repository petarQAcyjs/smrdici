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
class WeatherService(context: Context) {
    
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
        // Choose the most relevant advice based on temperature and other conditions
        val advice = when {
            weatherInfo.temperature > 35 -> 
                "🔥 Пали климу, напољу је пакао."
            weatherInfo.temperature > 30 -> 
                "🌡️ Преко 30 су најавили, покуваћемо се."
            weatherInfo.temperature > 25 -> {
                if (weatherInfo.humidity > 70) {
                    "💦 Није што је вруће, него што је нека спарина."
                } else {
                    "☀️ Таман да изведете ђецу у парк."
                }
            }
            weatherInfo.temperature > 15 -> 
                "🌤️ Ммммм, таман."
            weatherInfo.temperature > 5 -> {
                if (weatherInfo.windSpeed > 10) {
                    "💨 Ветровито је! Купите папирне марамице, Смрдићима ће нос да цури."
                } else {
                    "🧥 Смрзнуће се шарена гузица."
                }
            }
            weatherInfo.temperature > 0 -> 
                "❄️ Хладно је! Само да не заслине."
            weatherInfo.temperature > -10 -> 
                "🧤 Лол најавили минус."
            else -> 
                "🥶 Екстремно хладно! Избегавајте дуготрајно излагање хладноћи."
        }
        
        return advice
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