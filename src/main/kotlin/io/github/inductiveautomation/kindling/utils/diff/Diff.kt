package io.github.inductiveautomation.kindling.utils.diff

import io.github.inductiveautomation.kindling.utils.nextOrNull

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

class Difference<T> private constructor(
    val original: List<T>,
    val modified: List<T>,
    val additions: List<Diff.Addition<T>>,
    val deletions: List<Diff.Deletion<T>>,
) {
    val leftDiffList: List<Diff<T>> = buildList {
        original.mapIndexedTo(this) { index, s ->
            deletions.find { it.index == index } ?: Diff.NoChange(s, index, null)
        }

        var offset = 0
        additions.forEach {
            val existingDeletion = get(it.index)
            if (existingDeletion !is Diff.Deletion) {
                add(it.index, it)
                offset++
            }
        }
    }

    val rightDiffList: List<Diff<T>> = buildList {
        modified.mapIndexedTo(this) { index, s ->
            additions.find { it.index == index } ?: Diff.NoChange(s, index, null)
        }

        deletions.forEach {
            val existingAddition = getOrNull(it.index)

            if (existingAddition !is Diff.Addition) {
                add(it.index, it)
            }
        }
    }

    val unifiedDiffList: List<Diff<T>> = buildList {
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
        fun <U : Comparable<U>> of(
            original: List<U>,
            modified: List<U>,
            equalizer: (U, U) -> Boolean = { l, r -> compareValues(l, r) == 0 },
        ): Difference<U> {
            val lcs = LongestCommonSequence.of(original, modified, equalizer)

            val additions = modified.mapIndexedNotNull { index, item ->
                if (item in lcs) null else Diff.Addition(item, index)
            }
            val deletions = original.mapIndexedNotNull { index, item ->
                if (item in lcs) null else Diff.Deletion(item, index)
            }

            return Difference(original, modified, additions, deletions)
        }
    }
}
