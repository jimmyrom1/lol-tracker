import java.util.Properties

plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// La API key de Riot nunca se versiona: sale de local.properties (riot.apiKey) o de la variable
// de entorno RIOT_API_KEY. Sin ella la app funciona igual; solo pide la key al importar.
val localProperties = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.map { text ->
    Properties().apply { load(text.reader()) }
}
val usesServer = providers.environmentVariable("LOL_API_URL")
    .orElse(localProperties.map { it.getProperty("lolApi.url").orEmpty() }).getOrElse("").isNotBlank()

// Con lol-tracker-api la key vive en el servidor y NO se mete en el APK (de un APK se puede extraer).
val riotApiKey: String = if (usesServer) {
    ""
} else {
    providers.environmentVariable("RIOT_API_KEY")
        .orElse(localProperties.map { it.getProperty("riot.apiKey").orEmpty() }).getOrElse("")
}

android {
    namespace = "dev.jose.loltracker.core.data"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "RIOT_API_KEY", "\"$riotApiKey\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(libs.retrofit) // solo para leer el código HTTP de los errores
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
