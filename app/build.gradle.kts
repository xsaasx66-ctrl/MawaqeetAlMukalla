plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mawaqeetalmukalla"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mawaqeetalmukalla"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
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
    // One daily refresh, with no periodic polling loop.
    implementation("androidx.work:work-runtime:2.10.0")
}
