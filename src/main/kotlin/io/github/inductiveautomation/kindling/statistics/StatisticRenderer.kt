package io.github.inductiveautomation.kindling.statistics

import javax.swing.Icon
import javax.swing.JComponent

interface StatisticRenderer<T : Statistic> {
    val title: String
    val icon: Icon?

    fun T.subtitle(): String? {
        return null
    }

    fun T.render(): JComponent
}