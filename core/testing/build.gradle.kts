plugins {
    alias(libs.plugins.lol.android.library)
}

android {
    namespace = "dev.jose.loltracker.core.testing"
}

dependencies {
    api(projects.core.model)
    api(projects.core.data)
    api(projects.core.analytics)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
}
