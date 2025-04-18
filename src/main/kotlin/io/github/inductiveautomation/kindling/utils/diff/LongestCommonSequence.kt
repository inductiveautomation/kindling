package io.github.inductiveautomation.kindling.utils.diff

import kotlin.math.max

class LongestCommonSequence<T : Comparable<T>> private constructor(
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
        fun <U : Comparable<U>> of(
            a: List<U>,
            b: List<U>,
            equalizer: (U, U) -> Boolean = { l, r -> compareValues(l, r) == 0 },
        ) = LongestCommonSequence(a, b, equalizer).calculateLcs()
    }
}
