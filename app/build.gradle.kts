plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.petar.smrdici"
    compileSdk = 36

    defaultConfig {
        versionCode = providers.gradleProperty("versionCode").get().toInt()
        versionName = providers.gradleProperty("versionName").get()
        applicationId = "com.petar.smrdici"
        minSdk = 24
        //noinspection OldTargetApi Aplikacija sada koristi targetSdk = 36, što je najnovija verzija Android SDK-a (Android 16).
        //noinspection OldTargetApi
        targetSdk = 36        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Искључујемо учитавање native библиотеке libpenguin.so
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("Boolean", "DEBUG_VISUALIZATION", "false")
       }
    
   }
    
    // Додајемо конфигурацију за спречавање покушаја учитавања непостојећих .dm фајлова
    packaging {
        resources {
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("**/*.dm")
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Firebase BOM koristeći version catalog referencu
    implementation(platform(libs.firebase.bom))

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Firebase
    implementation(libs.play.services.auth)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.database.ktx)

    // Календарска компонента
    implementation(libs.calendar.compose)
    
    // Lottie за анимације
    implementation(libs.lottie.compose)

    // Додајемо или ажурирамо зависност за Material3
    implementation(libs.androidx.material3.library)
    
    // Gson за JSON сeријализацију
    implementation(libs.gson)
    
    // Додатне зависности за решавање проблема са Google API
    implementation(libs.play.services.auth)
    implementation(libs.play.services.base)
    
    // Coroutines sync - za Mutex implementaciju
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}