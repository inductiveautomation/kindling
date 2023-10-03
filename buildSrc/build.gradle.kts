plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jsoup)
}
