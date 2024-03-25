package io.github.inductiveautomation.kindling.xml.logback

import io.github.inductiveautomation.kindling.utils.NumericEntryField
import io.github.inductiveautomation.kindling.utils.rightBuddy
import javax.swing.JLabel

internal fun sizeEntryField(
    inputValue: Long?,
    units: String,
    callback: () -> Unit,
): NumericEntryField = NumericEntryField(inputValue).apply {
    rightBuddy = JLabel(units)
    addPropertyChangeListener("value") { callback() }
}

internal data class SelectedLogger(
    val name: String = "Logger name",
    val level: String = "INFO",
    val separateOutput: Boolean = false,
    val outputFolder: String = "\${LOG_HOME}\\\\AdditionalLogs\\\\",
    val filenamePattern: String = "${name.replace(".", "")}.%d{yyyy-MM-dd}.%i.log",
    val maxFileSize: Long = 10,
    val totalSizeCap: Long = 1000,
    val maxDaysHistory: Long = 5,
)

internal fun LogbackConfigData.toSelectedLoggers(): List<SelectedLogger> {
    return buildList {
        logger?.forEach { logger ->
            appender?.forEach { appender ->
                if (logger.name == appender.name) {
                    // If there is a separate output appender, it is guaranteed to have a rolling policy
                    val rollingPolicy = appender.rollingPolicy!!
                    val pathSplit = rollingPolicy.fileNamePattern.split("\\").filter { it.isNotBlank() }

                    add(
                        SelectedLogger(
                            name = logger.name,
                            level = logger.level ?: "INFO",
                            separateOutput = true,
                            outputFolder = pathSplit.minus(pathSplit.last()).joinToString(separator = "\\\\") + "\\\\",
                            filenamePattern = pathSplit.last().toString(),
                            maxFileSize = rollingPolicy.maxFileSize.filter(Char::isDigit).toLong(),
                            totalSizeCap = rollingPolicy.totalSizeCap.filter(Char::isDigit).toLong(),
                            maxDaysHistory = rollingPolicy.maxHistory.filter(Char::isDigit).toLong(),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Each selected logger will either output to a separate appender or use the default Sysout appender.
 * In either case, we need a <logger> element.
 * For those using a separate appender, we need to generate that <appender> element.
 */
internal fun LogbackConfigData.update(
    logHomeDirectory: LogHomeDirectory,
    scan: Boolean?,
    scanPeriod: String?,
    selectedLoggers: List<SelectedLogger>,
): LogbackConfigData {
    val separateOutputLoggers = selectedLoggers.filter(SelectedLogger::separateOutput)

    val loggerElements = selectedLoggers.map { logger ->
        Logger(
            name = logger.name,
            level = logger.level,
            additivity = false,
            appenderRef = if (logger.separateOutput) {
                mutableListOf(AppenderRef(logger.name))
            } else {
                mutableListOf(AppenderRef("SysoutAsync"), AppenderRef("DBAsync"))
            },
        )
    }

    val appenderElements = separateOutputLoggers.map { separateOutputLogger ->
        Appender(
            name = separateOutputLogger.name,
            className = "ch.qos.logback.core.rolling.RollingFileAppender",
            rollingPolicy = RollingPolicy(
                className = "ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy",
                fileNamePattern = separateOutputLogger.outputFolder + separateOutputLogger.filenamePattern,
                maxFileSize = separateOutputLogger.maxFileSize.toString() + "MB",
                totalSizeCap = separateOutputLogger.totalSizeCap.toString() + "MB",
                maxHistory = separateOutputLogger.maxDaysHistory.toString(),
            ),
            encoder = mutableListOf(
                Encoder(
                    pattern =
                    "%.-1p [%-30logger] [%d{YYYY/MM/dd HH:mm:ss, SSS}]: " +
                        "{%thread} %replace(%m){\"[\\r\\n]+\", \"\"} %X%n",
                ),
            ),
        )
    }

    return copy(
        logHomeDir = logHomeDirectory,
        scan = scan,
        scanPeriod = scanPeriod,
        logger = loggerElements,
        appender = appenderElements + LogbackConfigData.DEFAULT_APPENDERS,
    )
}
