plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.0"
val xrayCoreVersion = (project.findProperty("xrayCoreVersion") as String?) ?: "26.11.9"

android {
    namespace = "com.takvpn.app"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    defaultConfig {
        applicationId = "com.takvpn.app"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "XRAY_CORE_VERSION", "\"$xrayCoreVersion\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    val keystoreFile = rootProject.file("release.keystore")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val releaseRequested = gradle.startParameter.taskNames.any {
        it.contains("assembleRelease", ignoreCase = true) ||
            it.contains("bundleRelease", ignoreCase = true)
    }
    if (releaseRequested) {
        check(keystoreFile.exists() && !keystorePassword.isNullOrBlank()) {
            "Release build blocked: release.keystore and KEYSTORE_PASSWORD are required. Debug signing is never used for Release."
        }
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = keystorePassword ?: ""
            keyAlias = "takvpn"
            keyPassword = keystorePassword ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
