package io.github.inductiveautomation.kindling.idb.metrics

import java.util.Date

@JvmInline
value class Metric(val name: String)

data class MetricData(val value: Double, val timestamp: Date)
