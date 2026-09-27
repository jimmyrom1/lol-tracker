plugins {
    alias(libs.plugins.lol.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Solo las anotaciones y el runtime: el detalle de partida se guarda en Room como JSON.
    api(libs.kotlinx.serialization.json)
}
