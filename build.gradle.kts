import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.conveyor)
    alias(libs.plugins.dokka)
    application
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
    }
    maven {
        url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-thirdparty/")
    }
}

dependencies {
    // see gradle/libs.version.toml
    api(libs.serialization.json)
    api(libs.xerial.jdbc)
    api(libs.hsql)
    api(libs.miglayout)
    api(libs.jide.common)
    api(libs.swingx)
    api(libs.logback)
    api(libs.jsvg)
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

val downloadJavadocs = tasks.register<DownloadJavadocs>("downloadJavadocs") {
    urlsByVersion.set(
        mapOf(
            "8.1" to listOf(
                "https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.32/$java11AllClassPath",
                "https://docs.oracle.com/en/java/javase/17/docs/api/$java17AllClassPath",
                "https://www.javadoc.io/static/org.python/jython-standalone/2.7.3/$java8AllClassPath",
            ),
            "8.0" to listOf(
                "https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/$java11AllClassPath",
                "https://docs.oracle.com/en/java/javase/11/docs/api/$java11AllClassPath",
                "https://www.javadoc.io/static/org.python/jython-standalone/2.7.1/$java8AllClassPath",
            ),
            "7.9" to listOf(
                "https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/$java8AllClassPath",
                "https://docs.oracle.com/javase/8/docs/api/$java8AllClassPath",
                "https://www.javadoc.io/static/org.python/jython-standalone/2.5.3/$java8AllClassPath",
            ),
        ),
    )
    outputDir.set(project.layout.buildDirectory.dir("javadocs"))
}

kotlin {
    jvmToolchain {
        languageVersion.set(libs.versions.java.map(JavaLanguageVersion::of))
        vendor.set(JvmVendorSpec.AMAZON)
    }
    sourceSets {
        main {
            resources.srcDir(downloadJavadocs)
        }
    }
}

ktlint {
    reporters {
        reporter(CHECKSTYLE)
    }
}

private val java17AllClassPath = "allclasses-index.html"
private val java11AllClassPath = "allclasses.html"
private val java8AllClassPath = "allclasses-noframe.html"
