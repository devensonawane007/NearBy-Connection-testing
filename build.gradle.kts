plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  id("org.jetbrains.kotlin.android") version "2.0.21" apply false
  id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
}