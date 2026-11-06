plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.callguard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callguard.app"
        minSdk = 23
        // Keep target 32 for this prototype's foreground-service compatibility.
        // Revisit target SDK and service declarations before a production release.
        targetSdk = 32
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-ktx:1.15.0")
}
