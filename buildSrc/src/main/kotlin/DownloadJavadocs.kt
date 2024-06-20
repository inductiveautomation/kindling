import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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

    @get:InputDirectory
    abstract val tempDirectory: DirectoryProperty

    @TaskAction
    fun writeProperties() {
        if (project.gradle.startParameter.isOffline) return

        val listOfMaps = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            urls.get().map { url ->
                logger.info("Retrieving classes from $url")
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

        logger.debug("Retrieved ${outputMap.size} classes for ${version.get()}")

        tempDirectory.dir(version).get()
            .also {
                it.asFile.mkdirs()
            }
            .file("links.properties").asFile
            .printWriter()
            .use { writer ->
                for ((key, value) in outputMap) {
                    writer.append(key).append("=").append(value).appendLine()
                }
            }
    }
}
