plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.jose.loltracker.core.database"
}

androidComponents {
    // MigrationTestHelper lee los esquemas exportados como assets de los tests.
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
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
    testImplementation(libs.androidx.room.testing)
}
