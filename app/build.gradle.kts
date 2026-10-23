plugins {
    id("com.android.application")
    // Deve usar a MESMA versão de Kotlin do build.gradle.kts da raiz.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.apuracaoeleitoral"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "br.com.apuracaoeleitoral"
        minSdk = 26
        // Android 17 (API 37), mantendo compileSdk com a revisão 37.2.
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // Haze 2.0.1 requer metadados Kotlin 2.4; inclui os dois módulos do efeito de vidro.
    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
