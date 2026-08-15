import com.lagradost.cloudstream3.gradle.CloudstreamPlugin

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    setSrcDir("src/main/kotlin")
    authors = listOf("MTS")
}

android {
    defaultConfig {
        minSdk = 21
    }
}
