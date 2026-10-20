plugins {
    alias(libs.plugins.android.application)
}

// WhatsApp Web on the TV, with a remote-driven cursor and USB camera support. A separate app
// from Minimal Home: its own APK, launched from the app list.
android {
    namespace = "dev.minimal.tvchat"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.minimal.tvchat"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
}
