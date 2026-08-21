import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

val betaKeystorePath = System.getenv("GUIDE_BETA_KEYSTORE")
val betaStorePassword = System.getenv("GUIDE_BETA_STORE_PASSWORD")
val betaKeyAlias = System.getenv("GUIDE_BETA_KEY_ALIAS")
val betaKeyPassword = System.getenv("GUIDE_BETA_KEY_PASSWORD")

android {
    namespace = "com.guide.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guide.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "3.0.0"
    }

    signingConfigs {
        if (!betaKeystorePath.isNullOrBlank()) {
            create("beta") {
                storeFile = file(betaKeystorePath)
                storePassword = betaStorePassword
                keyAlias = betaKeyAlias
                keyPassword = betaKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("beta")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("beta")?.let { signingConfig = it }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
