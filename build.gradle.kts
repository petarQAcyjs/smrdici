val osName = System.getProperty("os.name").lowercase()
when {
    osName.contains("windows") -> {
        System.setProperty("org.gradle.java.home", "C:/Program Files/Android/Android Studio/jbr")
    }
    osName.contains("mac") -> {
        System.setProperty("org.gradle.java.home", "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
    }
    else -> run {
        // For Linux or other operating systems, we'll let Gradle use the default Java home
        println("Note: Using system default Java home for OS: $osName")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    //alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
