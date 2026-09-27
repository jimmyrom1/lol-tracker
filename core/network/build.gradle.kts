plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jose.loltracker.core.network"
}

dependencies {
    api(projects.core.model)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    api(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
