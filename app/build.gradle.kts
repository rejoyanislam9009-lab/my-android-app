plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rejoy.bdvpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rejoy.bdvpn"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        fun envString(name: String): String {
            val value = System.getenv(name).orEmpty()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            return "\"$value\""
        }

        // Server credentials are injected at build time. Do not commit live VPN keys.
        for (index in 1..3) {
            buildConfigField("String", "BD_VPN_${index}_NAME", envString("BD_VPN_${index}_NAME"))
            buildConfigField("String", "BD_VPN_${index}_ENDPOINT", envString("BD_VPN_${index}_ENDPOINT"))
            buildConfigField("String", "BD_VPN_${index}_SERVER_PUBLIC_KEY", envString("BD_VPN_${index}_SERVER_PUBLIC_KEY"))
            buildConfigField("String", "BD_VPN_${index}_CLIENT_PRIVATE_KEY", envString("BD_VPN_${index}_CLIENT_PRIVATE_KEY"))
            buildConfigField("String", "BD_VPN_${index}_CLIENT_ADDRESS", envString("BD_VPN_${index}_CLIENT_ADDRESS"))
        }
    }

    buildFeatures {
        buildConfig = true
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
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
