import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.conveyor)
    application
}

apply {
    plugin<DownloadJavadocsPlugin>()
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
    }
    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.Dansoftowner")
        }
    }
}

dependencies {
    // see gradle/libs.version.toml
    api(libs.serialization.json)
    api(libs.xerial.jdbc)
    api(libs.hsql)
    api(libs.zip4j)
    api(libs.miglayout)
    api(libs.jide.common)
    api(libs.swingx)
    api(libs.logback)
    api(libs.svgSalamander)
    api(libs.bundles.coroutines)
    api(libs.bundles.flatlaf)
    api(libs.bundles.ignition) {
        // Exclude transitive IA dependencies - we only need core Ignition classes for cache deserialization
        isTransitive = false
    }
    api(libs.excelkt)
    api(libs.jfreechart)
    api(libs.rsyntaxtextarea)
    runtimeOnly(libs.bundles.ia.transitive)

    testImplementation(libs.bundles.kotest)
}

group = "io.github.inductiveautomation"

application {
    mainClass.set("io.github.inductiveautomation.kindling.MainPanel")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(libs.versions.java.map(String::toInt).get())
}

ktlint {
    reporters {
        reporter(CHECKSTYLE)
    }
}
