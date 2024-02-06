package io.github.inductiveautomation.kindling.statistics

fun interface StatisticCalculator<T : Statistic> {
    suspend fun calculate(backup: GatewayBackup): T?
}
