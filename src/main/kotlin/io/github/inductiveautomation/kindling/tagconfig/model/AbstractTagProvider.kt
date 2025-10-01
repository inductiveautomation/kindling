package io.github.inductiveautomation.kindling.tagconfig.model

import io.github.inductiveautomation.kindling.tagconfig.TagConfigView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
sealed class AbstractTagProvider(
    val name: String,
    val uuid: Uuid,
    val description: String?,
    val enabled: Boolean,
) {
    abstract val providerStatistics: ProviderStatistics
    abstract val loadProvider: Job

    protected open val typesNode: Node = Node(
        config = TagConfig(name = "_types_", tagType = "Folder"),
        isMeta = true,
    )

    protected lateinit var providerNode: Node

    val isInitialized: Boolean
        get() = ::providerNode.isInitialized

    protected abstract val Node.parentType: Node?

    val Node.tagPath: String
        get() {
            if (this === providerNode) return ""
            val p = checkNotNull(getParent()) { "Parent is null! $this" }
            return when (p.name) {
                "" -> "[${this@AbstractTagProvider.name}]$name"
                else -> "${p.tagPath}/$name"
            }
        }

    fun exportToJson(path: Path) {
        path.outputStream().use {
            TagConfigView.TagExportJson.encodeToStream(providerNode, it)
        }
    }

    fun getProviderNode() = CoroutineScope(Dispatchers.Default).async {
        loadProvider.join()
        providerNode
    }

    protected abstract fun Node.resolveInheritance()
    protected abstract fun Node.resolveNestedUdtInstances()
    protected abstract fun Node.copyChildrenFrom(other: Node)
}
