plugins {
    id("com.android.application")
}

android {
    namespace = "com.anugrah.ctkkasir"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anugrah.ctkkasir"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "1.6.1"
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
}
