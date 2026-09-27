plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.android.compose)
}

android {
    namespace = "dev.jose.loltracker.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
