plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    implementation(project(":core"))
}
