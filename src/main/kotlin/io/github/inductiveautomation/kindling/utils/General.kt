package io.github.inductiveautomation.kindling.utils

import com.jidesoft.swing.CheckBoxListSelectionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.ServiceLoader
import kotlin.io.path.outputStream
import kotlin.math.log2
import kotlin.math.pow
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun String.truncate(length: Int = 20): String {
    return asIterable().joinToString(separator = "", limit = length)
}

fun String.toTempFile(prefix: String, suffix: String): Path {
    return Files.createTempFile(prefix, suffix).also { file ->
        file.outputStream().use { output ->
            this.byteInputStream().use { input ->
                input transferTo output
            }
        }
    }
}

inline fun <reified T> getLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java.name)
}

inline fun StringBuilder.tag(
    tag: String,
    vararg attributes: Pair<String, String>,
    content: StringBuilder.() -> Unit,
) {
    append("<")
    attributes.joinTo(this, prefix = tag, separator = " ", postfix = ">") { (key, value) ->
        " $key=\"$value\""
    }

    content(this)

    append("</").append(tag).append(">")
}

fun StringBuilder.tag(
    tag: String,
    vararg attributes: Pair<String, String>,
    content: String,
) {
    tag(tag, *attributes) {
        append(content)
    }
}

/**
 * Returns the mode (most common value) in a Grouping<T>
 */
fun <T> Grouping<T, Int>.mode(): Int? = eachCount().maxOfOrNull { it.key }

fun <T, U : Comparable<U>> List<T>.isSortedBy(keyFn: (T) -> U): Boolean {
    return asSequence().zipWithNext { a, b ->
        keyFn(a) <= keyFn(b)
    }.all { it }
}

/**
 * Creates and returns a new [Properties], loading keys from [inputStream] according to the loading strategy specified
 * via [loader], e.g. [Properties.load] (the default) or [Properties.loadFromXML].
 * The default loader closes [inputStream].
 */
fun Properties(
    inputStream: InputStream,
    loader: Properties.(InputStream) -> Unit = { stream -> stream.use(::load) },
): Properties = Properties().apply { loader(inputStream) }

private val prefix = arrayOf("", "k", "m", "g", "t", "p", "e", "z", "y")

fun Long.toFileSizeLabel(): String =
    when {
        this == 0L -> "0B"
        else -> {
            val digits = log2(toDouble()).toInt() / 10
            val precision = digits.coerceIn(0, 2)
            "%,.${precision}f${prefix[digits]}b".format(toDouble() / 2.0.pow(digits * 10.0))
        }
    }

operator fun MatchGroupCollection.getValue(
    thisRef: Any?,
    property: KProperty<*>,
): MatchGroup {
    return requireNotNull(get(property.name))
}

inline fun <reified S> loadService(): ServiceLoader<S> {
    return ServiceLoader.load(S::class.java)
}

fun String.escapeHtml(): String {
    return buildString {
        for (char in this@escapeHtml) {
            when (char) {
                '>' -> append("&gt;")
                '<' -> append("&lt;")
                else -> append(char)
            }
        }
    }
}

fun debounce(
    waitTime: Duration = 300.milliseconds,
    coroutineScope: CoroutineScope,
    destinationFunction: () -> Unit,
): () -> Unit {
    var debounceJob: Job? = null
    return {
        debounceJob?.cancel()
        debounceJob =
            coroutineScope.launch {
                delay(waitTime)
                destinationFunction()
            }
    }
}

/**
 * Transfers [this] to [output], closing both streams.
 */
infix fun InputStream.transferTo(output: OutputStream) {
    this.use { input ->
        output.use(input::transferTo)
    }
}

fun CheckBoxListSelectionModel.isAllSelected() = isSelectedIndex(allEntryIndex)

/**
 * Converts the contents of the InputStream to a human-readable binary string, with raw hex bytes on the left and
 * best effort ASCII decoding on the right.
 * Closes the input stream.
 */
fun InputStream.toHumanReadableBinary(): String {
    use { file ->
        val windowSize = 16
        return sequence {
            val buffer = ByteArray(windowSize)
            var numberOfBytesRead: Int
            do {
                numberOfBytesRead = file.readNBytes(buffer, 0, windowSize)

                // the last read might not be complete, so there could be stale data in the buffer
                val toRead = buffer.sliceArray(0 until numberOfBytesRead)

                @OptIn(ExperimentalStdlibApi::class)
                val hexBytes = toRead.toHexString(HEX_FORMAT)
                val decodedBytes = decodeBytes(toRead)
                yield("${hexBytes.padEnd(47)}  $decodedBytes")
            } while (numberOfBytesRead == windowSize)
        }.joinToString(separator = "\n")
    }
}

private fun decodeBytes(toRead: ByteArray): String {
    return String(
        CharArray(toRead.size) { i ->
            val byte = toRead[i]
            if (byte >= 0 && !Character.isISOControl(byte.toInt())) {
                Char(byte.toUShort())
            } else {
                '.'
            }
        },
    )
}

@OptIn(ExperimentalStdlibApi::class)
private val HEX_FORMAT = HexFormat {
    bytes {
        byteSeparator = " "
    }
}

fun <T> Iterator<T>.nextOrNull(): T? {
    return if (hasNext()) next() else null
}
