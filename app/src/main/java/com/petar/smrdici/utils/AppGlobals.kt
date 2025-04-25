package com.petar.smrdici.utils

import android.content.Context

/**
 * Pomoćna klasa za pristup aplikacijskom kontekstu iz statičkog konteksta
 * Posebno korisno za deserijalizatore koji nemaju direktan pristup aplikacijskom kontekstu
 */
class AppGlobals {
    
    companion object {
        private var appContext: Context? = null
        
        /**
         * Inicijalizacija u Application onCreate metodi
         */
        fun initialize(context: Context) {
            appContext = context.applicationContext
        }
        
        /**
         * Dobijanje aplikacijskog konteksta
         */
        fun getAppContext(): Context? {
            return appContext
        }
    }
} 