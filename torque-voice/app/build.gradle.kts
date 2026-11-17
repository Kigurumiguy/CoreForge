plugins { id("com.android.application") }
android {
 namespace = "com.torque.voice"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.torque.voice"
  minSdk = 26
  targetSdk = 35
  versionCode = 3
  versionName = "0.3.0"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
}
