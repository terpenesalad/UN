import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.unreel"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.unreel"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.3.0"
    }

    signingConfigs {
        // A fixed key so each new build installs over the previous one.
        // It lives in the repo, which is fine for a personal sideloaded app.
        // Use a private key (kept out of the repo) before publishing to the Play Store.
        create("sideload") {
            storeFile = file("unreel-signing.p12")
            storeType = "pkcs12"
            storePassword = "unreel-sideload"
            keyAlias = "unreel"
            keyPassword = "unreel-sideload"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
