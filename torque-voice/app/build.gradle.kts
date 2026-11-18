plugins { id("com.android.application") }
android {
 namespace = "com.torque.voice"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.torque.voice"
  minSdk = 26
  targetSdk = 35
  versionCode = 5
  versionName = "0.5.0"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
}

dependencies {
 implementation("com.alphacephei:vosk-android:0.3.75")
}
