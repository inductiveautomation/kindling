package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.utils.LongestCommonSequence.Companion.lcs
import kotlin.math.max

typealias DiffList<T> = List<Diff<T>>

sealed interface Diff<T> {
    val type: DiffType
    val value: T
    val index: Int

    class Addition<T>(
        override val type: DiffType,
        override val value: T,
        override val index: Int,
    ) : Diff<T>

    class Deletion<T>(
        override val type: DiffType,
        override val value: T,
        override val index: Int,
    ) : Diff<T>

    class NoChange<T>(
        override val type: DiffType,
        override val value: T,
        val preIndex: Int?,
        val postIndex: Int?,
    ) : Diff<T> {
        override val index: Int
            get() = (preIndex ?: postIndex)!!
    }

    companion object {
        operator fun <T> invoke(
            type: DiffType,
            value: T,
            index1: Int,
            index2: Int? = null,
        ): Diff<T> {
            return when (type) {
                DiffType.ADDITION -> Addition(type, value, index1)
                DiffType.DELETION -> Deletion(type, value, index1)
                DiffType.NOCHANGE -> NoChange(type, value, index1, index2)
            }
        }
    }
}

@Suppress("MemberVisibilityCanBePrivate")
class DiffData<T> private constructor(
    val preChange: List<T>,
    val postChange: List<T>,
    val additions: DiffList<T>,
    val deletions: DiffList<T>,
) {
    private constructor(
        preChange: List<T>,
        postChange: List<T>,
        equalityPredicate: (T, T) -> Boolean,
        lcs: List<T> = lcs(preChange, postChange, equalityPredicate),
    ) : this(
        preChange,
        postChange,
        additions = postChange.mapIndexedNotNull { index, item ->
            if (item in lcs) null else Diff(DiffType.ADDITION, item, index)
        }.sortedBy { it.index },
        deletions = preChange.mapIndexedNotNull { index, item ->
            if (item in lcs) null else Diff(DiffType.DELETION, item, index)
        }.sortedBy { it.index },
    )

    constructor(
        pre: List<T>,
        post: List<T>,
        equalityPredicate: (T, T) -> Boolean = { a, b -> a == b },
    ) : this(pre, post, equalityPredicate, lcs(pre, post, equalityPredicate))

    val leftDiffList = buildList {
        addAll(
            preChange.mapIndexed { index, s ->
                deletions.find { it.index == index } ?: Diff(DiffType.NOCHANGE, s, index)
            },
        )

        var offset = 0
        additions.forEach {
            val existingDeletion: Diff<T> = get(it.index)
            if (existingDeletion.type != DiffType.DELETION) {
                add(it.index, it)
                offset++
            }
        }
    }

    val rightDiffList = buildList {
        addAll(
            postChange.mapIndexed { index, s: T ->
                additions.find { it.index == index } ?: Diff(DiffType.NOCHANGE, s, index)
            },
        )

        deletions.forEach {
            val diffInList: Diff<T> = get(it.index)

            if (diffInList.type != DiffType.ADDITION) {
                add(it.index, it)
            }
        }
    }

    val unifiedDiffList: DiffList<T> = buildList {
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
                    add(Diff(leftItem.type, leftItem.value, leftItem.index, rightItem.index))
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
}

enum class DiffType(val symbol: String) {
    DELETION("-"),
    ADDITION("+"),
    NOCHANGE(""),
}

// Don't ask me to explain how this works
class LongestCommonSequence<T>(
    private val a: List<T>,
    private val b: List<T>,
    private val equalityPredicate: (T, T) -> Boolean,
) {
    private val lengthMatrix = Array(a.size + 1) { Array(b.size + 1) { -1 } }

    private fun buildLengthMatrix(
        i: Int,
        j: Int,
    ): Int {
        if (i == 0 || j == 0) {
            lengthMatrix[i][j] = 0
            return 0
        }

        if (lengthMatrix[i][j] != -1) return lengthMatrix[i][j]

        val result: Int = if (equalityPredicate(a[i - 1], b[j - 1])) {
            1 + buildLengthMatrix(i - 1, j - 1)
        } else {
            max(buildLengthMatrix(i - 1, j), buildLengthMatrix(i, j - 1))
        }

        lengthMatrix[i][j] = result
        return result
    }

    fun calculateLcs(): List<T> {
        var i = a.size
        val j = b.size
        buildLengthMatrix(i, j)

        return buildList {
            for (n in j.downTo(1)) {
                if (lengthMatrix[i][n] == lengthMatrix[i][n - 1]) {
                    continue
                } else {
                    add(0, b[n - 1])
                    i--
                }
            }
        }
    }

    companion object {
        fun <T> lcs(
            a: List<T>,
            b: List<T>,
            equalityPredicate: (T, T) -> Boolean,
        ) = LongestCommonSequence(a, b, equalityPredicate).calculateLcs()
    }
}
