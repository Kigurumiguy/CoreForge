plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 }
android {
 namespace = "com.torque.voice"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.torque.voice"
  minSdk = 26
  ndk { abiFilters += listOf("arm64-v8a","x86_64") }
  targetSdk = 35
  versionCode = 6
  versionName = "0.6.0"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
}

dependencies {
 implementation("com.alphacephei:vosk-android:0.3.75")
 implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
