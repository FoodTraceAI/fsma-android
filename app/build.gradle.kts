plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.foodtraceai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.foodtraceai"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation ("com.jakewharton.threetenabp:threetenabp:1.3.1")


    // Jetpack Compose UI dependencies
    implementation(platform(libs.androidx.compose.bom)) // BOM for Compose version alignment
    implementation("androidx.compose.ui:ui") // Use BOM version
    implementation("androidx.compose.material3:material3") // Use BOM version
    implementation("androidx.compose.ui:ui-tooling-preview") // Use BOM version

    debugImplementation("androidx.compose.ui:ui-tooling")

    // QR code and PTI label scanning library (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.4.1")  // Core ZXing dependency

    // Retrofit for making API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp for network requests and logging
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")  // For logging network requests

    // Kotlin Coroutines for background processing
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0") // Update to latest
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0") // Update to latest

    // AppCompat for dialogs and backward compatibility
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Permissions handling for camera and other resources
    implementation("androidx.core:core-ktx:1.10.0")

    // AndroidX DataStore for key-value data storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Remove PostgreSQL dependency if not used in the app
}
