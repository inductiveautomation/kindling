plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.conveyor)
    alias(libs.plugins.dokka)
    application
}

repositories {
    mavenCentral()
    maven(url = "https://nexus.inductiveautomation.com/repository/public/")
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
    mainClass = "io.github.inductiveautomation.kindling.MainPanel"
}

tasks {
    test {
        useJUnitPlatform()
    }
}

val downloadJavadocs =
    tasks.register<DownloadJavadocs>("downloadJavadocs") {
        urlsByVersion = mapOf(
            "8.1" to
                listOf(
                    "https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.32/allclasses.html",
                    "https://docs.oracle.com/en/java/javase/17/docs/api/allclasses-index.html",
                    "https://www.javadoc.io/static/org.python/jython-standalone/2.7.3/allclasses-noframe.html",
                ),
            "8.0" to
                listOf(
                    "https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/allclasses.html",
                    "https://docs.oracle.com/en/java/javase/11/docs/api/allclasses.html",
                    "https://www.javadoc.io/static/org.python/jython-standalone/2.7.1/allclasses-noframe.html",
                ),
            "7.9" to
                listOf(
                    "https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/allclasses-noframe.html",
                    "https://docs.oracle.com/javase/8/docs/api/allclasses-noframe.html",
                    "https://www.javadoc.io/static/org.python/jython-standalone/2.5.3/allclasses-noframe.html",
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

spotless {
    ratchetFrom = "main"

    format("misc") {
        target("*.gradle", ".gitattributes", ".gitignore")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    java {
        palantirJavaFormat()
        formatAnnotations()
    }
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
