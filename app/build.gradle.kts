// Конфігурація модуля додатку (папка app/)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // потрібен для Firebase
}

android {
    namespace = "com.example.messenger"
    compileSdk = 34

    defaultConfig {
        // Цей package name обов'язково вкажи в Firebase (див. README)!
        applicationId = "com.example.messenger"
        minSdk = 24   // Android 7.0 і новіші
        targetSdk = 34
        versionCode = 2
        versionName = "0.0.3-Alpha1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // ---- Jetpack Compose (інтерфейс) ----
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Android-обгортки ----
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // ---- Firebase (месенджер) ----
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")

    // дозволяє викликати .await() для Firebase-задач у корутинах
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
