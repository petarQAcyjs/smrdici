package com.petar.smrdici.notification

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduler for the daily weather notification that runs at 8:00 AM
 */
object DailyWeatherScheduler {
    private const val TAG = "DailyWeatherScheduler"
    
    /**
     * Schedule the daily weather notification to run at 8:00 AM every day
     */
    fun scheduleDailyWeatherNotification(context: Context, location: String = "Belgrade") {
        Log.d(TAG, "Setting up daily weather notification for location: $location")
        
        // Calculate initial delay to 8:00 AM
        val initialDelay = calculateInitialDelayTo8AM()
        
        // Input data for the worker
        val inputData = Data.Builder()
            .putString("LOCATION", location)
            .build()
        
        // Set constraints - ideally we want network connectivity
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        // Build the periodic work request - runs every 24 hours
        val dailyWeatherRequest = PeriodicWorkRequestBuilder<DailyWeatherWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        
        // Schedule the work with a unique name, replacing any existing one
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyWeatherWorker.WORKER_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            dailyWeatherRequest
        )
        
        Log.d(TAG, "Daily weather notification scheduled to run in ${initialDelay / (60 * 60 * 1000)} hours and ${(initialDelay / (60 * 1000)) % 60} minutes")
    }
    
    /**
     * Cancel the daily weather notification
     */
    fun cancelDailyWeatherNotification(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DailyWeatherWorker.WORKER_NAME)
        Log.d(TAG, "Daily weather notification cancelled")
    }
    
    /**
     * Calculate the delay until the next 8:00 AM
     */
    private fun calculateInitialDelayTo8AM(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        // Set target time to 8:00 AM today
        calendar.set(Calendar.HOUR_OF_DAY, 8)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // If 8:00 AM today has already passed, set to 8:00 AM tomorrow
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        // Calculate delay
        return calendar.timeInMillis - now
    }
} 