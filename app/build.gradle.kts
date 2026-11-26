plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// En GitHub, cada compilación tiene un número que sube solo; en tu PC vale 1.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.mandobt"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mandobt"
        minSdk = 28
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
