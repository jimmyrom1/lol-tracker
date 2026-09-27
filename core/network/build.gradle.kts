import java.util.Properties

plugins {
    alias(libs.plugins.lol.android.library)
    alias(libs.plugins.lol.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Opcional: usar lol-tracker-api en vez de llamar a Riot desde el móvil. Se configura en
// local.properties (lolApi.url, lolApi.token) o con LOL_API_URL / LOL_API_TOKEN.
val localProperties = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.map { text ->
    Properties().apply { load(text.reader()) }
}
fun setting(property: String, env: String): String =
    providers.environmentVariable(env).orElse(localProperties.map { it.getProperty(property).orEmpty() }).getOrElse("")

android {
    namespace = "dev.jose.loltracker.core.network"
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "LOL_API_URL", "\"${setting("lolApi.url", "LOL_API_URL")}\"")
        buildConfigField("String", "LOL_API_TOKEN", "\"${setting("lolApi.token", "LOL_API_TOKEN")}\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    api(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
}
