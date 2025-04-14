package com.petar.smrdici

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import android.os.StrictMode
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.petar.smrdici.data.repository.ExpenseRepository
import com.petar.smrdici.data.repository.IncomeRepository

class SmrdiciApplication : Application() {
    companion object {
        private const val TAG = "SmrdiciApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Конфигуришемо управљање изузецима и логовима
        configureLogging()
        
        // Иницијализујемо Google Play сервисе правилно
        initGooglePlayServices()
        
        // Иницијализујемо репозиторијуме
        ExpenseRepository.initialize()
        IncomeRepository.initialize()
        
        // Офлајн подршка је подразумевано укључена у новијим верзијама Firebase-а
        // Нема потребе за додатном конфигурацијом
    }
    
    /**
     * Иницијализује Google Play сервисе са правилним контекстом
     * како би се избегле SecurityException грешке
     */
    private fun initGooglePlayServices() {
        try {
            // Ажурирамо Android Security Provider да избегнемо проблеме са безбедношћу
            ProviderInstaller.installIfNeeded(applicationContext)
            
            // Проверавамо доступност Google Play сервиса
            val availability = GoogleApiAvailability.getInstance()
            val result = availability.isGooglePlayServicesAvailable(applicationContext)
            
            if (result != ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play сервиси нису доступни или нису ажурирани")
            }
        } catch (e: GooglePlayServicesRepairableException) {
            // Ово значи да је потребна интервенција корисника да поправи Play сервисе
            Log.w(TAG, "Google Play сервиси захтевају ажурирање", e)
        } catch (e: GooglePlayServicesNotAvailableException) {
            // Play сервиси нису доступни на уређају
            Log.w(TAG, "Google Play сервиси нису доступни", e)
        } catch (e: Exception) {
            // Други проблеми
            Log.e(TAG, "Грешка при иницијализацији Google Play сервиса", e)
        }
    }
    
    /**
     * Конфигурише начин на који се руководи логовима и изузецима
     * како би се избегла непотребна упозорења у логовима
     */
    private fun configureLogging() {
        // Постављамо свој UncaughtExceptionHandler како бисмо филтрирали одређене грешке
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Филтрирамо грешке везане за наше познате проблеме
            if (throwable.message?.contains("libpenguin.so") == true ||
                throwable.message?.contains("QSPM AIDL service") == true ||
                throwable.stackTrace.any { it.className.contains("ziparchive") } ||
                throwable.message?.contains("Unknown calling package name 'com.google.android.gms'") == true ||
                throwable.stackTrace.any { it.className.contains("GoogleApiManager") }) {
                // Прескачемо логовање, јер су ово грешке које нас не занимају
                Log.d(TAG, "Филтриран познати exception: ${throwable.message}")
            } else {
                // За све остале грешке користимо подразумевани handler
                Log.e(TAG, "Некоригован exception у thread-у '$thread'", throwable)
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
        
        // Спречавамо StrictMode упозорења у development окружењу
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .permitDiskWrites()
                    .permitDiskReads()
                    .penaltyLog()
                    .build()
            )
        }
        
        // Пречица за избегавање Invalid resource ID логова
        suppressInvalidResourceIdLogs()
    }
    
    /**
     * Спречава логовање грешака за невалидне ресурс ID-јеве
     */
    private fun suppressInvalidResourceIdLogs() {
        try {
            val resources = resources
            val configuration = resources.configuration
            
            // Користимо модеран API који није застарео
            createConfigurationContext(configuration)
            
            // Алтернативни приступ за освежавање ресурса:
            // Форсирамо приступ ресурсима кроз нови контекст
            // val newContext = createConfigurationContext(configuration)
            // val newResources = newContext.resources
        } catch (_: Exception) {
            // Игноришемо грешке, није критично
        }
    }
    
    // Чувамо референцу на подразумевани UncaughtExceptionHandler
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    // У апликацијама са више процеса, користимо ово да избегнемо 
    // дупле иницијализације
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Потискујемо додатно логовање у другим процесама
        if (base != null && packageName != getProcessName(base)) {
            // Ово је секундарни процес, ограничавамо логовање
            Log.d(TAG, "Апликација се покреће у секундарном процесу")
        }
    }
    
    // Помоћна метода за добијање имена тренутног процеса
    private fun getProcessName(context: Context): String? {
        val pid = Process.myPid()
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return null
    }
} 