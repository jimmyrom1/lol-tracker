import java.util.Properties

plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
}

// La API key de Riot nunca se versiona: sale de local.properties (riot.apiKey) o de la variable
// de entorno RIOT_API_KEY. Sin ella la app funciona igual; solo pide la key al importar.
val riotApiKey: String = providers.environmentVariable("RIOT_API_KEY").orElse(
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.map { text ->
        Properties().apply { load(text.reader()) }.getProperty("riot.apiKey").orEmpty()
    },
).getOrElse("")

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

    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
