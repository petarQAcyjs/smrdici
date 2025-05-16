// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Set Java home based on OS
val osName = System.getProperty("os.name").lowercase()
when {
    osName.contains("windows") -> {
        System.setProperty("org.gradle.java.home", "C:/Program Files/Android/Android Studio/jbr").also { }
    }
    osName.contains("mac") -> {
        System.setProperty("org.gradle.java.home", "/Applications/Android Studio.app/Contents/jbr/Contents/Home").also { }
    }
    else -> run {
        // For Linux or other operating systems, we'll let Gradle use the default Java home
        println("Note: Using system default Java home for OS: $osName")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") apply false
}
