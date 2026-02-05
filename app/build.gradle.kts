import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

fun resolveSigningProperty(name: String): String? {
    return keystoreProperties.getProperty(name) ?: System.getenv(name)
}

android {
    namespace = "com.example.dailynote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.dailynote"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = resolveSigningProperty("STORE_FILE")
            val storePasswordValue = resolveSigningProperty("STORE_PASSWORD")
            val keyAliasValue = resolveSigningProperty("KEY_ALIAS")
            val keyPasswordValue = resolveSigningProperty("KEY_PASSWORD")

            if (
                !storeFilePath.isNullOrBlank() &&
                !storePasswordValue.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue

                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/NOTICE.md",
                "/META-INF/LICENSE.md"
            )
        }
    }
}

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.name.contains("Release", ignoreCase = true) }
    val hasSigningCredentials =
        !resolveSigningProperty("STORE_FILE").isNullOrBlank() &&
        !resolveSigningProperty("STORE_PASSWORD").isNullOrBlank() &&
        !resolveSigningProperty("KEY_ALIAS").isNullOrBlank() &&
        !resolveSigningProperty("KEY_PASSWORD").isNullOrBlank()

    if (releaseRequested && !hasSigningCredentials) {
        throw GradleException(
            "Release signing is not configured. Set STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD in keystore.properties or environment variables."
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
