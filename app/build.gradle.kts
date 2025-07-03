import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val fernetKey: String = localProperties.getProperty("fernetKey") ?: ""
val encryptedApiKey1: String = localProperties.getProperty("encryptedApiKey1") ?: ""
val encryptedApiKey2: String = localProperties.getProperty("encryptedApiKey2") ?: ""


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tpk.widgetspro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tpk.widgetspro"
        minSdk = 31
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY_1", "\"$fernetKey\"")
        buildConfigField("String", "API_KEY_2", "\"$encryptedApiKey1\"")
        buildConfigField("String", "API_KEY_3", "\"$encryptedApiKey2\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        applicationVariants.configureEach {
            outputs.configureEach {
                (this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl)?.outputFileName =
                    "Widgets-Pro-${buildType.name}-v$versionName.apk"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.api)
    implementation(libs.provider)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.gson)
    implementation (libs.androidx.security.crypto)
    implementation(libs.lazysodium.android)
    implementation(libs.jna)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.picasso)
    implementation(libs.androidx.preference.ktx)
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.29")
    implementation("androidx.media:media:1.7.0")
}