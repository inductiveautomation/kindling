package io.github.inductiveautomation.kindling.idb.tagconfig.model

import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics.DependentStatistic.Companion.dependentStatistic
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics.MappedStatistic.Companion.mappedStatistic
import io.github.inductiveautomation.kindling.idb.tagconfig.model.ProviderStatistics.QuantitativeStatistic.Companion.quantitativeStatistic
import java.util.Locale
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ProviderStatistics private constructor(
    private val statsMap: MutableMap<String, ProviderStatistic<*>>,
) : Map<String, ProviderStatistics.ProviderStatistic<*>> by statsMap {
    constructor() : this(mutableMapOf())

    val orphanedTags = ListStatistic("orphanedTags", fun(_: ListStatistic<Node>, _: Node) = Unit)

    val totalAtomicTags by quantitativeStatistic {
        if (it.statistics.isAtomicTag) value++
    }

    val totalFolders by quantitativeStatistic {
        if (it.statistics.isFolder) value++
    }

    val totalUdtInstances by quantitativeStatistic {
        if (it.statistics.isUdtInstance) value++
    }

    val totalTagsWithAlarms by quantitativeStatistic {
        if (it.statistics.hasAlarms) value++
    }

    val totalUdtDefinitions by quantitativeStatistic {
        if (it.statistics.isUdtDefinition) value++
    }

    val totalAlarms by quantitativeStatistic {
        value += it.statistics.numAlarms
    }

    val totalTagsWithHistory by quantitativeStatistic {
        if (it.statistics.historyEnabled == true) value++
    }

    val totalTagsWithEnabledScripts by quantitativeStatistic {
        if (it.statistics.hasScripts) value++
    }

    val totalEnabledScripts by quantitativeStatistic {
        value += it.statistics.numScripts
    }

    val dataTypes by mappedStatistic {
        if (it.statistics.isAtomicTag) {
            value.compute(it.statistics.dataType ?: DEFAULT_DATA_TYPE) { _, v ->
                if (v == null) 1 else v + 1
            }
        }
    }

    val valueSources by mappedStatistic {
        if (it.statistics.isAtomicTag) {
            value.compute(it.statistics.dataSource ?: DEFAULT_VALUE_SOURCE) { _, v ->
                if (v == null) 1 else v + 1
            }
        }
    }

    val totalOrphanedTags by dependentStatistic(orphanedTags) { it.size }

    fun processNodeForStatistics(node: Node) {
        for (stat in values) stat.processNode(node)
    }

    override fun toString(): String = values.joinToString("\n")

    companion object {
        private const val DEFAULT_DATA_TYPE = "Int4"
        private const val DEFAULT_VALUE_SOURCE = "memory"
    }

    sealed class ProviderStatistic<T>(val name: String) {
        abstract val value: T

        val humanReadableName = name.splitCamelCase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        override fun toString(): String = "$humanReadableName: $value"

        abstract fun processNode(node: Node)

        companion object {
            private fun String.splitCamelCase(): String = replace(
                String.format(
                    "%s|%s|%s",
                    "(?<=[A-Z])(?=[A-Z][a-z])",
                    "(?<=[^A-Z])(?=[A-Z])",
                    "(?<=[A-Za-z])(?=[^A-Za-z])",
                ).toRegex(),
                " ",
            )
        }
    }

    class QuantitativeStatistic(
        name: String,
        private val processNode: QuantitativeStatistic.(node: Node) -> Unit,
    ) : ProviderStatistic<Int>(name) {
        override var value: Int = 0
        override fun processNode(node: Node) = processNode(this, node)

        companion object {
            fun quantitativeStatistic(
                processNode: QuantitativeStatistic.(node: Node) -> Unit,
            ) = PropertyDelegateProvider { thisRef: ProviderStatistics, property ->
                val quantitative = QuantitativeStatistic(property.name, processNode)
                thisRef.statsMap[property.name] = quantitative
                ReadOnlyProperty { _: ProviderStatistics, _ -> quantitative }
            }
        }
    }

    class MappedStatistic(
        name: String,
        private val processNode: MappedStatistic.(node: Node) -> Unit,
    ) : ProviderStatistic<MutableMap<String, Int>>(name) {
        override val value: MutableMap<String, Int> = mutableMapOf()
        override fun processNode(node: Node) = processNode(this, node)

        override fun toString(): String = name + value.entries.joinToString("\n|- ", prefix = "|- ") { (key, value) -> "$key: $value" }

        companion object {
            fun mappedStatistic(
                processNode: MappedStatistic.(node: Node) -> Unit,
            ) = PropertyDelegateProvider { thisRef: ProviderStatistics, property ->
                val mapped = MappedStatistic(property.name, processNode)
                thisRef.statsMap[property.name] = mapped
                ReadOnlyProperty { _: ProviderStatistics, _ -> mapped }
            }
        }
    }

    class ListStatistic<T>(
        name: String,
        private val processNode: ListStatistic<T>.(node: Node) -> Unit,
    ) : ProviderStatistic<MutableList<T>>(name) {
        override val value: MutableList<T> = mutableListOf()
        override fun processNode(node: Node) = processNode(this, node)
    }

    class DependentStatistic<T, P>(
        name: String,
        private val dependsOn: ProviderStatistic<P>,
        private val transform: (P) -> T,
    ) : ProviderStatistic<T>(name) {
        override fun processNode(node: Node) = Unit
        override val value: T
            get() = transform(dependsOn.value)

        companion object {
            fun <T, P> dependentStatistic(
                dependsOn: ProviderStatistic<P>,
                transform: (P) -> T,
            ) = PropertyDelegateProvider { thisRef: ProviderStatistics, property ->
                val dependent = DependentStatistic(property.name, dependsOn, transform)
                thisRef.statsMap[property.name] = dependent
                ReadOnlyProperty { _: ProviderStatistics, _ -> dependent }
            }
        }
    }
}
