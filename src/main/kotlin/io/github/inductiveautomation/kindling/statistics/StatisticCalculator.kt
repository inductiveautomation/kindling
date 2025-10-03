package io.github.inductiveautomation.kindling.statistics

import io.github.inductiveautomation.kindling.utils.MajorVersion

fun interface StatisticCalculator<T : Statistic> {
    suspend fun calculate(backup: GatewayBackup): T?
}

abstract class CalculatorSupport<T : Statistic>(
    private val calculatorMap: Map<MajorVersion, StatisticCalculator<T>>,
) : Map<MajorVersion, StatisticCalculator<T>> by calculatorMap
