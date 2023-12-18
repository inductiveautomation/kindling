plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jsoup)
}

spotless {
    ratchetFrom = "bd4c7ac33cc45657dc837a67d1fccbd6c24fd731"
    kotlin {
        // https://github.com/diffplug/spotless/pull/1890#issuecomment-1827263031
        @Suppress("INACCESSIBLE_TYPE")
        ktlint()
    }
    kotlinGradle {
        // https://github.com/diffplug/spotless/pull/1890#issuecomment-1827263031
        @Suppress("INACCESSIBLE_TYPE")
        ktlint()
    }
}
