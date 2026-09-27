plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.jose.loltracker.core.database"
}

room {
    // El esquema exportado se versiona en git: así cada migración futura se puede revisar y testear.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
}
