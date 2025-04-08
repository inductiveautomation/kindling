import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup
import java.util.concurrent.Executors
import java.util.concurrent.Future

abstract class DownloadJavadocs : DefaultTask() {
    init {
        group = "build"
        description = "Downloads class manifests from web-hosted Javadocs"
    }

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val urls: ListProperty<String>

    @get:Internal
    abstract val baseOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    val versionOutputDirectory: Provider<Directory> = baseOutputDirectory.dir(version)

    @TaskAction
    fun writeProperties() {
        if (project.gradle.startParameter.isOffline) {
            logger.warn("""
                Gradle is in offline mode. Skipping Javadoc download for ${version.get()}. 
                Output directory ${versionOutputDirectory.get()} may be missing or stale.
                """.trimIndent())
            // Ensure the output directory exists even in offline mode so Gradle doesn't complain
            // but don't modify contents if it already exists.
            versionOutputDirectory.get().asFile.mkdirs()
            return
        }

        val listOfMaps = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            urls.get().map { url ->
                logger.info("Retrieving classes from $url for version ${version.get()}")
                executor.submit<Map<String, String>> {
                    Jsoup.connect(url).get()
                        .select("""a[href][title*="class"], a[href][title*="interface"]""")
                        .distinctBy { it.attr("abs:href") }
                        .associate { a ->
                            val className = a.text()
                            val packageName = a.attr("title").substringAfterLast(' ')

                            "$packageName.$className" to a.absUrl("href")
                        }
                }
            }.map(Future<Map<String, String>>::get)
        }

        val outputMap = buildMap {
            for (map in listOfMaps) {
                putAll(map)
            }
        }

        logger.info("Retrieved ${outputMap.size} classes for ${version.get()}")

        val outputDir = versionOutputDirectory.get().asFile

        val outputFile = outputDir.resolve("links.properties")

        outputFile.printWriter().use { writer ->
            outputMap.toSortedMap().forEach { (key, value) ->
                writer.append(key).append("=").append(value).appendLine()
            }
        }
        logger.lifecycle("Wrote Javadoc links for version ${version.get()} to $outputFile")
    }
}
