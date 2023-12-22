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
    ratchetFrom = "e639479c2bef3553f16c08f8114b4a177c0ebf09"
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
