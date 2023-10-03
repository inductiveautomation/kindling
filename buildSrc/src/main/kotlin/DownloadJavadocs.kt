import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup
import java.net.URI

abstract class DownloadJavadocs : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val urls: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputDir.convention(project.layout.buildDirectory.dir("javadocs"))
    }

    @TaskAction
    fun downloadJavadoc() {
        if (project.gradle.startParameter.isOffline) return

        val destination = outputDir.asFile.get()
        val versionKey = version.get()

        destination.resolve(versionKey).apply {
            mkdirs()
            resolve("links.properties").printWriter().use { writer ->
                for (javadoc in urls.get()) {
                    logger.info("Fetching all classes from $javadoc")

                    try {
                        URI.create(javadoc).toURL().openStream().use { inputstream ->
                            Jsoup.parse(inputstream, Charsets.UTF_8.name(), javadoc)
                                .select("""a[href][title*="class"], a[href][title*="interface"]""")
                                .distinctBy { a -> a.attr("abs:href") }
                                .forEach { a ->
                                    val className = a.text()
                                    val packageName = a.attr("title").substringAfterLast(' ')

                                    writer.append(packageName).append('.').append(className)
                                        .append('=').append(a.absUrl("href"))
                                        .appendLine()
                                }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to fetch $javadoc", e)
                    }
                }
            }
        }

        destination.resolve("versions.txt").appendText(versionKey + "\n")
    }
}
