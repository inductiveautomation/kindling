package io.github.inductiveautomation.kindling.statistics

interface StatisticCalculator<T : Statistic> {
    suspend fun calculate(backup: GatewayBackup.InternalDatabase): T?

    suspend fun calculate(backup: GatewayBackup.Filesystem): T?
}
