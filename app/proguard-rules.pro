# Retrofit + kotlinx.serialization traen sus propias reglas de R8 en los AAR/JAR.
# Solo hace falta conservar los DTO que se deserializan por reflexión de genéricos.
-keep class dev.jose.loltracker.core.network.** { *; }
