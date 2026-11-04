plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cleanproxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleanproxy"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

kotlin {
    jvmToolchain(17)
}


dependencies {
    implementation("com.github.singbox-android:libbox:1.14.4")
}
