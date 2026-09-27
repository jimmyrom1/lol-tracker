plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
}

android {
    namespace = "dev.jose.loltracker.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.turbine)
}
