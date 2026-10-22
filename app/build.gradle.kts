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

// Each commit builds with its own versionCode, the minutes from 2020 to the commit, so a newer
// commit always installs over an older one, squash merges included. Diagnostics shows the commit,
// so which build a TV runs can be told at a glance. Without git (a source download) both fall back.
fun git(vararg args: String): String? = runCatching {
    providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
}.getOrNull()
val gitCommitMinutes = git("log", "-1", "--format=%ct")?.toLongOrNull()?.let { ((it - 1_577_836_800L) / 60).toInt() } ?: 1
val gitCommit = (git("rev-parse", "--short", "HEAD") ?: "unknown") +
    if (git("status", "--porcelain", "--untracked-files=no").isNullOrEmpty()) "" else "-modified"

android {
    namespace = "dev.minimal.tvhome"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.minimal.tvhome"
        // 28 = Android 9, which Fire OS 7 sticks run (Fire TV Stick HD, 4K 1st gen, Cube 2nd gen).
        minSdk = 28
        targetSdk = 36
        versionCode = gitCommitMinutes
        versionName = "0.2.0"
        buildConfigField("String", "UNSPLASH_ACCESS_KEY", "\"$unsplashAccessKey\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
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
