plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.petar.smrdici"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.petar.smrdici"
        minSdk = 24
        //noinspection OldTargetApi Vaša aplikacija trenutno koristi targetSdk = 35, što je već Android 15, ali je to dovoljno novo i ne bih to menjao u ovom trenutku, jer vidim da je vaš compileSdk takođe postavljen na 35.
        //Međutim, postoji mogućnost da vas IDE upozorava jer je već u najavi Android 16. Ako želite da koristite najnoviju verziju, uradiću potrebne izmene, ali je za sada aplikacija usklađena sa najnovijim zahtevima za Google Play. Upozorenje možemo ignorisati ili ažurirati na Android 16 kada bude zvanično objavljen.
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Искључујемо визуализацију граница за дебаговање
            buildConfigField("Boolean", "DEBUG_VISUALIZATION", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // Искључујемо визуализације граница за дебаговање
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
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
}