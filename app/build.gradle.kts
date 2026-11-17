plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.laurence.ojtattendance"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.laurence.ojtattendance"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")
            val storePass = System.getenv("RELEASE_STORE_PASSWORD")
            val keyAliasValue = System.getenv("RELEASE_KEY_ALIAS")
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
            if (!storeFilePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasValue
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
