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
    maven("https://nexus.inductiveautomation.com/repository/public/")
}

dependencies {
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
    api(libs.poi)
    api(libs.excelkt) {
        // bringing in POI manually, since this wrapper appears unmaintained
        isTransitive = false
    }
    api(libs.jfreechart)
    api(libs.questdb)
    api(libs.rsyntaxtextarea)
    api(libs.bundles.jackson)
    runtimeOnly(libs.bundles.ia.transitive)
    testImplementation(libs.bundles.kotest)
}

group = "io.github.inductiveautomation"

application {
    mainClass = "io.github.inductiveautomation.kindling.MainPanel"
    applicationDefaultJvmArgs += listOf(
        "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
        "--add-exports=java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED",
        "--add-exports=java.desktop/apple.laf=ALL-UNNAMED",
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
    )
}

val javadocDirectory = project.layout.buildDirectory.dir("javadocs")

tasks {
    test {
        useJUnitPlatform()
    }

    val download79 by registering(DownloadJavadocs::class) {
        version = "7.9"
        urls = listOf(
            "https://files.inductiveautomation.com/sdk/javadoc/ignition79/7921/allclasses-noframe.html",
            "https://docs.oracle.com/javase/8/docs/api/allclasses-noframe.html",
            "https://www.javadoc.io/static/org.python/jython-standalone/2.5.3/allclasses-noframe.html",
        )
        baseOutputDirectory = javadocDirectory
    }
    val download80 by registering(DownloadJavadocs::class) {
        version = "8.0"
        urls = listOf(
            "https://files.inductiveautomation.com/sdk/javadoc/ignition80/8.0.14/allclasses.html",
            "https://docs.oracle.com/en/java/javase/11/docs/api/allclasses.html",
            "https://www.javadoc.io/static/org.python/jython-standalone/2.7.1/allclasses-noframe.html",
        )
        baseOutputDirectory = javadocDirectory
    }
    val download81 by registering(DownloadJavadocs::class) {
        version = "8.1"
        urls = listOf(
            "https://files.inductiveautomation.com/sdk/javadoc/ignition81/8.1.48/allclasses-index.html",
            "https://docs.oracle.com/en/java/javase/17/docs/api/allclasses-index.html",
            "https://www.javadoc.io/static/org.python/jython-standalone/2.7.3/allclasses-noframe.html",
        )
        baseOutputDirectory = javadocDirectory
    }
    processResources {
        duplicatesStrategy = DuplicatesStrategy.WARN
        dependsOn(download79, download80, download81)
    }
}

kotlin {
    jvmToolchain {
        languageVersion = libs.versions.java.map(JavaLanguageVersion::of)
        vendor = JvmVendorSpec.AMAZON
    }
    sourceSets {
        main {
            resources.srcDir(javadocDirectory)
        }
    }
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xcontext-parameters"))
    }
}

spotless {
    ratchetFrom = "e639479c2bef3553f16c08f8114b4a177c0ebf09"
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
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
