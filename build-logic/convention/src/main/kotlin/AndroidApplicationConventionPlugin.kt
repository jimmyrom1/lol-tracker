import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.int("compileSdk")
            defaultConfig {
                minSdk = libs.int("minSdk")
                targetSdk = libs.int("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests {
                isIncludeAndroidResources = true
                // Robolectric necesita acceso a internals de java.io en JDK 17+.
                all {
                    it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
                    // Hilt genera fuentes de test aunque el módulo no tenga tests propios.
                    it.failOnNoDiscoveredTests.set(false)
                }
            }
        }
    }
}
