import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.heavylifters.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.heavylifters.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Find password across all common variable names
            val password = System.getenv("STORE_PASS")
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")

            // Key alias is "upload" as shown in the workflow log
            val alias = System.getenv("KEY_ALIAS")
                ?: System.getenv("KEYSTORE_ALIAS")
                ?: "upload"

            // Look for keystore in env var or common restored file locations
            val candidatePaths = listOfNotNull(
                System.getenv("KEYSTORE_PATH"),
                System.getenv("KEYSTORE_FILE"),
                "upload.keystore",
                "app/upload.keystore",
                "release.keystore",
                "app/release.keystore",
                "${System.getProperty("java.io.tmpdir")}/release.keystore",
                "${System.getProperty("java.io.tmpdir")}/upload.keystore"
            )

            val resolvedFile = candidatePaths
                .map { File(it) }
                .firstOrNull { it.exists() && it.isFile }

            println(">>> [Signing] Resolved Keystore: ${resolvedFile?.absolutePath}")
            println(">>> [Signing] Key Alias: $alias | Password Set: ${!password.isNullOrBlank()}")

            if (resolvedFile != null && !password.isNullOrBlank()) {
                storeFile = resolvedFile
                storePassword = password
                keyAlias = alias
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}
