package io.github.inductiveautomation.kindling.core

import io.github.inductiveautomation.kindling.core.Detail.BodyLine

data class Detail(
    val title: String,
    val message: String? = null,
    val details: Map<String, String?> = emptyMap(),
    val body: List<BodyLine> = emptyList(),
) {
    data class BodyLine(val text: String, val link: String? = null)

    companion object {
        val EMPTY_LINE = BodyLine("", null)

        operator fun invoke(
            title: String,
            message: String? = null,
            details: Map<String, String?> = emptyMap(),
            body: List<String> = emptyList(),
        ) = Detail(
            title,
            message,
            details,
            body.map(::BodyLine),
        )
    }
}

fun MutableList<BodyLine>.add(line: String, link: String? = null) {
    add(BodyLine(line, link))
}
