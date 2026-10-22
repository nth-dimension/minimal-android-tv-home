import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Unsplash access key: `unsplash.accessKey=...` in local.properties (not committed), or UNSPLASH_ACCESS_KEY.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val unsplashAccessKey = (localProperties.getProperty("unsplash.accessKey") ?: System.getenv("UNSPLASH_ACCESS_KEY") ?: "")
    .trim()
    .filter { it.isLetterOrDigit() || it == '-' || it == '_' }

android {
    namespace = "dev.minimal.tvhome"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.minimal.tvhome"
        // 28 = Android 9, which Fire OS 7 sticks run (Fire TV Stick HD, 4K 1st gen, Cube 2nd gen).
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"
        buildConfigField("String", "UNSPLASH_ACCESS_KEY", "\"$unsplashAccessKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Personal sideload build: sign with the local debug key so no keystore setup is needed.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests; the Android stub throws.
    testImplementation(libs.json)
}
