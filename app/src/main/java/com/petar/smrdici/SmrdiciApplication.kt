package com.petar.smrdici

import android.app.Application

class SmrdiciApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Офлајн подршка је подразумевано укључена у новијим верзијама Firebase-а
        // Нема потребе за додатном конфигурацијом
    }
} 