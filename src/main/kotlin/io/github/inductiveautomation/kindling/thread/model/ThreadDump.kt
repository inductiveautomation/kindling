package io.github.inductiveautomation.kindling.thread.model

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.General.DefaultEncoding
import io.github.inductiveautomation.kindling.core.ToolOpeningException
import io.github.inductiveautomation.kindling.utils.FileFilterableCollection
import io.github.inductiveautomation.kindling.utils.getValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.lang.Thread.State as ThreadState

@Serializable
@ConsistentCopyVisibility
data class ThreadDump internal constructor(
    val version: String,
    val threads: List<Thread>,
    @SerialName("deadlocks")
    val deadlockIds: List<Long> = emptyList(),
) : FileFilterableCollection<Thread?> {
    @Transient
    override val items = threads

    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
        }

        fun fromStream(stream: InputStream): ThreadDump? {
            val text = stream.reader(DefaultEncoding.currentValue).readText()
            return try {
                JSON.decodeFromString(serializer(), text)
            } catch (ex: SerializationException) {
                val lines = text.lines()
                if (lines.size <= 2) throw ToolOpeningException("Not a fully formed thread dump")
                val firstLine = lines.first()

                val deadlockIds = if (lines[2].contains("Deadlock")) {
                    deadlocksPattern.findAll(lines[3])
                        .map { match -> match.value.toLong() }
                        .toList()
                } else {
                    emptyList()
                }

                ThreadDump(
                    version = versionPattern.find(firstLine)?.value
                        ?: throw ToolOpeningException("Unable to read thread dump; possibly malformed"),
                    threads = when {
                        firstLine.contains(":") -> parseScript(text)
                        else -> parseWebPage(text)
                    },
                    deadlockIds = deadlockIds,
                )
            }
        }

        private val versionPattern = """[78]\.\d\.\d\d?.*""".toRegex()

        private val deadlocksPattern = """\d+""".toRegex()

        private val scriptThreadRegex = """
            "(?<name>.*)"
            \s*CPU:\s(?<cpu>\d{1,3}\.\d{2})%
            \s*java\.lang\.Thread\.State:\s(?<state>\w+_?\w+)
            \s*(?<stack>[\S\s]+?)[\r\n]{2,}
        """.trimIndent().toRegex(RegexOption.COMMENTS)

        private val webThreadRegex = """
            (?<isDaemon>Daemon )?Thread \[(?<name>.*)] id=(?<id>\d*), \((?<state>\w*)\)\s*(?:\(native\))?\s*
            ?(?<stack>\s{4}[\S\s]+?)?
            ?(?=(?:Daemon )?Thread |")
        """.trimIndent().toRegex()
        private val webThreadMonitorRegex = "owns monitor: (?<monitor>.*)".toRegex()
        private val webThreadSynchronizerRegex = "owns synchronizer: (?<synchronizer>.*)".toRegex()
        private val webThreadBlockerRegex = "waiting for: (?<lock>\\S+)(?: \\(owned by (?<owner>\\d*))?".toRegex()
        private val webThreadStackRegex = "^(?<line>(?!waiting |owns ).*)$".toRegex(RegexOption.MULTILINE)

        private fun parseScript(dump: String): List<Thread> = scriptThreadRegex.findAll(dump).map { matcher ->
            val name by matcher.groups
            val cpu by matcher.groups
            val state by matcher.groups
            val stack by matcher.groups

            Thread(
                id = name.value.hashCode().toLong(),
                name = name.value,
                cpuUsage = cpu.value.toDouble(),
                state = ThreadState.valueOf(state.value),
                isDaemon = false,
                stacktrace = stack.value.lines().map(String::trim),
            )
        }.toList()

        private fun parseWebPage(dump: String): List<Thread> = webThreadRegex.findAll(dump).map { matcher ->
            val isDaemon = matcher.groups["isDaemon"]?.value != null
            val name by matcher.groups
            val id by matcher.groups
            val state by matcher.groups
            val stack = matcher.groups["stack"]?.value?.trimIndent() ?: ""
            val monitors = webThreadMonitorRegex.findAll(stack).mapNotNull { monitorMatcher ->
                monitorMatcher.groups["monitor"]?.value?.let {
                    Thread.Monitors(it)
                }
            }.toList()
            val synchronizers = webThreadSynchronizerRegex.findAll(stack).mapNotNull { synchronizerMatcher ->
                synchronizerMatcher.groups["synchronizer"]?.value
            }.toList()
            val blocker = webThreadBlockerRegex.find(stack)?.groups?.let { blockerMatcher ->
                Thread.Blocker(blockerMatcher["lock"]!!.value, blockerMatcher["owner"]?.value?.toLongOrNull())
            }
            val parsedStack = webThreadStackRegex.findAll(stack).mapNotNull { stackMatcher ->
                stackMatcher.groups["line"]?.value
            }.toList()

            Thread(
                id = id.value.toLong(),
                name = name.value,
                state = ThreadState.valueOf(state.value),
                isDaemon = isDaemon,
                blocker = blocker,
                lockedMonitors = monitors,
                lockedSynchronizers = synchronizers,
                stacktrace = parsedStack,
            )
        }.toList()
    }
}
