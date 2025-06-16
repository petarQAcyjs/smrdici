package com.petar.smrdici

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.jakewharton.threetenabp.AndroidThreeTen
import com.petar.smrdici.data.repository.RepositoryManager
import com.petar.smrdici.notification.DailyWeatherScheduler
import com.petar.smrdici.notification.NotificationManager
import com.petar.smrdici.util.TimeFormatUtil
import com.petar.smrdici.utils.AppGlobals
import com.petar.smrdici.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Glavna aplikacijska klasa koja se inicijalizuje pri pokretanju aplikacije.
 */
class SmrdiciApplication : Application(), Configuration.Provider {
    companion object {
        private const val TAG = "SmrdiciApplication"
        
        // Application instance
        private lateinit var instance: SmrdiciApplication
        
        fun getInstance(): SmrdiciApplication {
            return instance
        }
        
        fun getNotificationManager(): NotificationManager {
            return instance.notificationManager
        }
        
        // Application-level CoroutineScope for long-running operations
        // Uses SupervisorJob so that failure of one child doesn't cancel others
        // This is a safer alternative to GlobalScope
        fun getAppScope(): CoroutineScope {
            return instance.applicationScope
        }
    }
    
    // NotificationManager moved to instance field to avoid static context reference
    private lateinit var notificationManager: NotificationManager
    
    // Application-level CoroutineScope that lives for the entire app lifecycle
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Store instance
        instance = this
        
        Log.d(TAG, "Inicijalizacija Smrdici aplikacije")
        
        // Initialize ThreeTenABP
        AndroidThreeTen.init(this)
        
        // Inicijalizacija log sistema
        initLogging()
        
        // Force 24-hour time format
        TimeFormatUtil.force24HourFormat(this)
        
        // Иницијализујемо Google Play сервисе правилно
        initGooglePlayServices()
        
        // Иницијализујемо репозиторијуме
        initializeRepositories()
        
        // Иницијализујемо AppGlobals
        AppGlobals.initialize(applicationContext)
        
        // Initialize NotificationManager
        initializeNotifications()
        
        // Офлајн подршка је подразумевано укључена у новијим верзијама Firebase-а
        // Нема потребе за додатном конфигурацијом
    }
    
    private fun initLogging() {
        // Postavljamo opcije logovanja - može biti konfigurisano na osnovu build tipa
        // ili BuildConfig.DEBUG uslova
        val isDebugBuild = BuildConfig.DEBUG
        
        // Osnovna konfiguracija
        LogUtils.Config.ENABLE_DETAILED_LOGS = isDebugBuild
        
        // Podešavamo nivo detaljnosti u zavisnosti od build tipa
        LogUtils.Config.DETAIL_LEVEL = if (isDebugBuild) {
            LogUtils.DetailLevel.NORMAL
        } else {
            LogUtils.DetailLevel.MINIMAL
        }
        
        // Broj stavki za prikazivanje u listama
        LogUtils.Config.MAX_ITEMS_TO_LOG = 5
        
        // Isključujemo neke manje bitne tagove
        LogUtils.Config.DISABLED_TAGS.add("CursorInputConnection")
        LogUtils.Config.DISABLED_TAGS.add("ProfileInstaller")
        LogUtils.Config.DISABLED_TAGS.add("Choreographer")
        
        // Selektivno kontrolišemo kategorije logova
        if (!isDebugBuild) {
            // U produkciji, isključimo neke kategorije da smanjimo količinu logova
            LogUtils.Config.disableCategory("ui")
            LogUtils.Config.disableCategory("transaction")
            
            // Isključujemo detalje za expense i income kategorije
            LogUtils.Config.disableCategory("expense")
            LogUtils.Config.disableCategory("income")
        } else {
            // U debug verziji, podesimo nivo detalja za različite kategorije
            LogUtils.Config.enableCategory("debug")
            LogUtils.Config.enableCategory("testing")
            
            // Ograničimo broj stavki za prikaz
            if (LogUtils.Config.DETAIL_LEVEL == LogUtils.DetailLevel.VERBOSE) {
                LogUtils.Config.MAX_ITEMS_TO_LOG = 10
            } else {
                LogUtils.Config.MAX_ITEMS_TO_LOG = 3  // U normalnom režimu prikazujemo samo 3 stavke
            }
        }
        
        Log.i(TAG, "Logging inicijalizovan: detaljno logovanje = ${LogUtils.Config.ENABLE_DETAILED_LOGS}, nivo = ${LogUtils.Config.DETAIL_LEVEL}")
        
        // Set up exception handling
        setupExceptionHandling()
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
     * Поставља руковање изузецима за целу апликацију
     */
    private fun setupExceptionHandling() {
        // Постављамо свој UncaughtExceptionHandler како бисмо филтрирали одређене грешке
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Филтрирамо грешке везане за наше познате проблеме
            if (throwable.message?.contains("libpenguin.so") == true ||
                throwable.message?.contains("QSPM AIDL service") == true ||
                throwable.stackTrace.any { it.className.contains("ziparchive") } ||
                throwable.message?.contains("Unknown calling package name 'com.google.android.gms'") == true ||
                throwable.stackTrace.any { it.className.contains("GoogleApiManager") } ||
                (throwable is SecurityException && throwable.message?.contains("Unknown calling package name 'com.google.android.gms'") == true)) {
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
    
    private fun initializeRepositories() {
        Log.d(TAG, "Inicijalizacija repozitorijuma")
        
        // Dobavljamo instance repozitorijuma kroz RepositoryManager
        val expenseRepository = RepositoryManager.getExpenseRepositoryForFinance()
        val incomeRepository = RepositoryManager.getIncomeRepositoryForFinance()
        val accountRepository = RepositoryManager.getAccountRepositoryForFinance(this)
        
        // Međusobno povezivanje repozitorijuma (ako je potrebno)
        // Npr. ExpenseRepository zahteva AccountRepository za ažuriranje balansa računa
        expenseRepository.setAccountRepository(accountRepository)
        incomeRepository.setAccountRepository(accountRepository)
        
        Log.d(TAG, "Repozitorijumi inicijalizovani")
    }
    
    private fun initializeNotifications() {
        // Initialize the NotificationManager
        notificationManager = NotificationManager(applicationContext)
        
        // Setup daily weather notification if weather-aware notifications are enabled
        if (notificationManager.weatherAwareEnabled) {
            DailyWeatherScheduler.scheduleDailyWeatherNotification(applicationContext)
        } else {
            // Cancel any existing scheduled weather notifications
            DailyWeatherScheduler.cancelDailyWeatherNotification(applicationContext)
        }
        
        Log.d(TAG, "Notification system initialized")
    }
    
    // WorkManager configuration
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
