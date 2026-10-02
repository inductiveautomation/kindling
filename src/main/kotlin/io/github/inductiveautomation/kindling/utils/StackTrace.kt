package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.Detail.BodyLine
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Advanced.HyperlinkStrategy
import io.github.inductiveautomation.kindling.core.LinkHandlingStrategy.OpenInIde
import java.util.Properties

typealias StackElement = String
typealias StackTrace = List<StackElement>

private val classnameRegex = """(.*/)?(?<path>[^\s\d$]*)[.$].*\(((?<file>.*\..*):(?<line>\d+)|.*)\)""".toRegex()

fun StackElement.toBodyLine(version: String): BodyLine = MajorVersion.lookup(version)?.let {
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

enum class MajorVersion(val version: String) {
    SevenNine("7.9") {
        override fun matches(version: String) = version.startsWith("7.9")
    },
    EightZero("8.0") {
        override fun matches(version: String) = version.startsWith("8.0")
    },
    EightOne("8.1") {
        override fun matches(version: String) = version.startsWith("8.1")
    },
    EightThree("8.3") {
        override fun matches(version: String) = version.startsWith("8.3") || version == "dev"
    },
    ;

    val classMap: Properties? by lazy {
        Properties().also { properties ->
            this::class.java.getResourceAsStream("/$version/links.properties")?.use(properties::load)
        }
    }

    internal abstract fun matches(version: String): Boolean

    companion object {
        fun lookup(version: String): MajorVersion? = entries.firstOrNull { it.matches(version) }
    }
}
