import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jsoup.Jsoup
import javax.inject.Inject

abstract class DownloadJavadocs @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val urls: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        @Suppress("LeakingThis")
        outputDir.convention(project.layout.buildDirectory.dir("javadocs"))
    }

    @TaskAction
    fun downloadJavadoc() {
        if (project.gradle.startParameter.isOffline) return

        urls.get().forEach { javadocUrl ->
            workerExecutor.noIsolation().submit(DownloadWorker::class.java) {
                url.set(javadocUrl)
                linksFile.set(outputDir.file(version.map { "$it/links.properties" }))
            }
        }
    }

    interface DownloadWorkerParameters : WorkParameters {
        val url: Property<String>
        val linksFile: RegularFileProperty
    }

    abstract class DownloadWorker @Inject constructor(
        private val parameters: DownloadWorkerParameters,
    ) : WorkAction<DownloadWorkerParameters> {
        override fun execute() {
            val propertiesFile = parameters.linksFile.get().asFile
            propertiesFile.parentFile.mkdir()

            Jsoup.connect(parameters.url.get()).get()
                .select("""a[href][title*="class"], a[href][title*="interface"]""")
                .distinctBy { it.attr("abs:href") }
                .forEach { a ->
                    val className = a.text()
                    val packageName = a.attr("title").substringAfterLast(' ')

                    propertiesFile.appendText("$packageName.$className=${a.absUrl("href")}\n")
                }
        }
    }
}
