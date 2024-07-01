package io.github.inductiveautomation.kindling.utils.diff

import io.github.inductiveautomation.kindling.utils.nextOrNull

typealias DiffList<T> = List<Diff<T>>

sealed interface Diff<T> {
    val key: String
    val value: T
    val index: Int

    data class Addition<T>(
        override val value: T,
        override val index: Int,
    ) : Diff<T> {
        override val key = "+"
    }

    data class Deletion<T>(
        override val value: T,
        override val index: Int,
    ) : Diff<T> {
        override val key = "-"
    }

    data class NoChange<T>(
        override val value: T,
        val preIndex: Int?,
        val postIndex: Int?,
    ) : Diff<T> {
        override val key = "|"

        override val index: Int
            get() = (preIndex ?: postIndex)!!
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class DiffUtil private constructor(
    val original: List<String>,
    val modified: List<String>,
    val additions: List<Diff.Addition<String>>,
    val deletions: List<Diff.Deletion<String>>,
) {
    val leftDiffList = buildList {
        original.mapIndexedTo(this) { index, s ->
            deletions.find { it.index == index } ?: Diff.NoChange(s, index, null)
        }

        var offset = 0
        additions.forEach {
            val existingDeletion: Diff<String> = get(it.index)
            if (existingDeletion !is Diff.Deletion) {
                add(it.index, it)
                offset++
            }
        }
    }

    val rightDiffList = buildList {
        modified.mapIndexedTo(this) { index, s ->
            additions.find { it.index == index } ?: Diff.NoChange(s, index, null)
        }

        deletions.forEach {
            val existingAddition: Diff<String>? = getOrNull(it.index)

            if (existingAddition !is Diff.Addition) {
                add(it.index, it)
            }
        }
    }

    val unifiedDiffList: DiffList<String> = buildList {
        val leftList = leftDiffList.iterator()
        val rightList = rightDiffList.iterator()

        var leftItem = leftList.nextOrNull()
        var rightItem = rightList.nextOrNull()

        while (leftItem != null || rightItem != null) {
            while (leftItem is Diff.Addition) {
                leftItem = leftList.nextOrNull()
            }

            while (rightItem is Diff.Deletion) {
                rightItem = rightList.nextOrNull()
            }

            when {
                leftItem is Diff.NoChange && rightItem is Diff.NoChange -> {
                    add(Diff.NoChange(leftItem.value, leftItem.index, rightItem.index))
                    leftItem = leftList.nextOrNull()
                    rightItem = rightList.nextOrNull()
                }

                leftItem is Diff.Deletion -> {
                    add(leftItem)
                    leftItem = leftList.nextOrNull()
                }

                rightItem is Diff.Addition -> {
                    add(rightItem)
                    rightItem = rightList.nextOrNull()
                }
            }
        }
    }

    companion object {
        fun create(
            original: List<String>,
            modified: List<String>,
            equalizer: (String, String) -> Boolean = String::equals,
        ): DiffUtil {
            val lcs = LongestCommonSequence.calculate(original, modified, equalizer)

            val additions = modified.mapIndexedNotNull { index, item ->
                if (item in lcs) null else Diff.Addition(item, index)
            }
            val deletions = original.mapIndexedNotNull { index, item ->
                if (item in lcs) null else Diff.Deletion(item, index)
            }

            return DiffUtil(original, modified, additions, deletions)
        }
    }
}
