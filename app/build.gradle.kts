plugins {
  alias(libs.plugins.android.application)
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.samekanprivatetrekroom"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.samekanprivatetrekroom"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Nearby Connections
  implementation("com.google.android.gms:play-services-nearby:18.7.0")

  // Play Services Location (GPS)
  implementation("com.google.android.gms:play-services-location:21.2.0")

  // Room Database
  val roomVersion = "2.8.4"
  implementation("androidx.room:room-runtime:$roomVersion")
  implementation("androidx.room:room-ktx:$roomVersion")
  kapt("androidx.room:room-compiler:$roomVersion")

  // JSON Serialization
  implementation("com.google.code.gson:gson:2.10.1")

  // ZXing QR Code Library
  implementation("com.google.zxing:core:3.5.3")

  // CameraX for offline barcode scanning
  val cameraxVersion = "1.3.4"
  implementation("androidx.camera:camera-core:$cameraxVersion")
  implementation("androidx.camera:camera-camera2:$cameraxVersion")
  implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
  implementation("androidx.camera:camera-view:$cameraxVersion")

  // Material Icons Extended for Compose
  implementation("androidx.compose.material:material-icons-extended")
}
