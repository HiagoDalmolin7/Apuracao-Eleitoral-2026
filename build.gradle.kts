// Projeto: Apuração Eleitoral 2026
// Android Gradle Plugin 9 já inclui suporte nativo a Kotlin.
// Forçamos Kotlin Gradle Plugin 2.4.21 no classpath para evitar
// que o compilador integrado utilize a versão anterior do AGP.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
