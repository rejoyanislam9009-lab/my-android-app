import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google Services is applied only when a package-matching Firebase config exists.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.globalcall.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.globalcall.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"

        // Public Firebase client configuration reused from the repository's existing
        // guide-c4c2c project. Cloud mode initializes these lazily only after the user
        // explicitly opens it; instant room calls remain independent from Firebase.
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyAGr0BN1CbqiL_WcgZ8br20Np14zp_8NaE\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"guide-c4c2c\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"1:512749667590:android:cbd98fa7db7988e523ca87\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"512749667590\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"guide-c4c2c.firebasestorage.app\"")
        buildConfigField("String", "MEETING_BASE_URL", "\"https://meet.jit.si\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
