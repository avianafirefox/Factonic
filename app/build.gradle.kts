import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.factonic.game"
    // Google Play requires app updates to target Android 16 (API 36) since August 31, 2026.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.factonic.app"
        minSdk = 26
        targetSdk = 36
        // Play Console: the last upload was versionCode 4. Raise this for every new upload.
        versionCode = 5
        versionName = "1.0.0"
    }

    signingConfigs {
        // One fixed key for every build - local and GitHub Actions - so each new APK installs as an
        // update over the previous one. By default that is the shared key in app/factonic.jks.
        // For a store release, point these environment variables at your own private key instead:
        //   FACTONIC_KEYSTORE, FACTONIC_KEYSTORE_PASSWORD, FACTONIC_KEY_ALIAS, FACTONIC_KEY_PASSWORD
        create("factonic") {
            fun env(name: String, fallback: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback
            storeFile = file(env("FACTONIC_KEYSTORE", "factonic.jks"))
            storePassword = env("FACTONIC_KEYSTORE_PASSWORD", "factonic")
            keyAlias = env("FACTONIC_KEY_ALIAS", "factonic")
            keyPassword = env("FACTONIC_KEY_PASSWORD", "factonic")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("factonic")
        }
        debug {
            signingConfig = signingConfigs.getByName("factonic")
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
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
