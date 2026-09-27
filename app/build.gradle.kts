plugins {
    alias(libs.plugins.lol.android.application)
    alias(libs.plugins.lol.android.compose)
    alias(libs.plugins.lol.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jose.loltracker"

    defaultConfig {
        applicationId = "dev.jose.loltracker"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin keystore propio en el repo: la release se firma con la clave de debug para poder probarla.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.analytics)
    implementation(projects.feature.matches)
    implementation(projects.feature.stats)
    implementation(projects.feature.draft)
    implementation(projects.feature.profile)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)

    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
}
