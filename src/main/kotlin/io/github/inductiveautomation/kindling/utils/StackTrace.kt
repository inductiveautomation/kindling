package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.LinkHandlingStrategy.OpenInIde
import java.util.Properties

typealias StackElement = String
typealias StackTrace = List<StackElement>

private val classnameRegex = """(.*/)?(?<path>[^\s\d$]*)[.$].*\(((?<file>.*\..*):(?<line>\d+)|.*)\)""".toRegex()

fun StackElement.toBodyLine(version: String): BodyLine {
    return MajorVersion.lookup(version)?.let {
        val escapedLine = this.escapeHtml()
        val matchResult = classnameRegex.find(this)

        if (matchResult != null) {
            val path by matchResult.groups
            if (HyperlinkStrategy.currentValue == OpenInIde) {
                val file = matchResult.groups["file"]?.value
                val line = matchResult.groups["line"]?.value?.toIntOrNull()
                if (file != null && line != null) {
                    BodyLine(escapedLine, "http://localhost/file?file=$file&line=$line")
                } else {
                    BodyLine(escapedLine)
                }
            } else {
                val url = it.classMap?.get(path.value) as String?
                BodyLine(escapedLine, url)
            }
        } else {
            BodyLine(escapedLine)
        }
    } ?: BodyLine(this)
}

@Suppress("ktlint:standard:trailing-comma-on-declaration-site")
enum class MajorVersion(val version: String) {
    SevenNine("7.9"),
    EightZero("8.0"),
    EightOne("8.1");

    val classMap: Properties? by lazy {
        Properties().also { properties ->
            this::class.java.getResourceAsStream("/$version/links.properties")?.use(properties::load)
        }
    }

    companion object {
        private val versionCache = LinkedHashMap<String, MajorVersion?>().apply {
            put("dev", EightOne)
            repeat(22) { patch ->
                put("7.9.$patch", SevenNine)
            }
            repeat(18) { patch ->
                put("8.0.$patch", EightZero)
            }
            repeat(33) { patch ->
                put("8.1.$patch", EightOne)
            }
        }

        fun lookup(version: String): MajorVersion? {
            return versionCache.getOrPut(version) {
                entries.firstOrNull { majorVersion ->
                    version.startsWith(majorVersion.version)
                }
            }
        }
    }
}
