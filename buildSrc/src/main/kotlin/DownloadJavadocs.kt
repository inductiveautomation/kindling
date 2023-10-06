import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.charset
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup

abstract class DownloadJavadocs : DefaultTask() {
    @get:Input
    abstract val urlsByVersion: MapProperty<String, List<String>>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val client = HttpClient(CIO)

    @TaskAction
    fun downloadJavadoc() {
        if (project.gradle.startParameter.isOffline) return

        val destination = outputDir.asFile.get()
        val versionsAndUrls = urlsByVersion.get()

        val async = runBlocking(Dispatchers.IO) {
            versionsAndUrls.mapValues { (_, urls) ->
                urls.map { url ->
                    async {
                        try {
                            val response = client.get(url)
                            val charset = response.charset() ?: Charsets.UTF_8

                            Jsoup.parse(response.bodyAsChannel().toInputStream(), charset.name(), url)
                                .select("""a[href][title*="class"], a[href][title*="interface"]""")
                                .distinctBy { a -> a.attr("abs:href") }
                                .map { a ->
                                    val className = a.text()
                                    val packageName = a.attr("title").substringAfterLast(' ')

                                    "$packageName.$className=${a.absUrl("href")}"
                                }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
                    .awaitAll()
                    .flatten()
            }
        }

        for ((version, property) in async) {
            destination.resolve(version).apply { 
                mkdirs()
                resolve("links.properties").printWriter().use { writer ->
                    for (line in property) {
                        writer.println(line)
                    }
                }
            }
        }
    }
}
