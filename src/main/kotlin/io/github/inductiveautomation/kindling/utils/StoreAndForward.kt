package io.github.inductiveautomation.kindling.utils

import com.inductiveautomation.ignition.gateway.history.BasicHistoricalRecord
import com.inductiveautomation.ignition.gateway.history.ScanclassHistorySet
import io.github.inductiveautomation.kindling.cache.AliasingObjectInputStream
import io.github.inductiveautomation.kindling.cache.model.AbstractDataset
import io.github.inductiveautomation.kindling.cache.model.AlarmJournalData
import io.github.inductiveautomation.kindling.cache.model.AlarmJournalSFGroup
import io.github.inductiveautomation.kindling.cache.model.AuditProfileData
import io.github.inductiveautomation.kindling.cache.model.BasicDataset
import io.github.inductiveautomation.kindling.cache.model.RemoteEvent
import io.github.inductiveautomation.kindling.cache.model.ScriptedSFData
import io.github.inductiveautomation.kindling.core.Detail
import java.io.Serializable

const val TRANSACTION_GROUP_DATA = "Transaction Group Data"

fun Serializable.toDetail(): Detail = when (this) {
    is BasicHistoricalRecord -> toDetail()
    is ScanclassHistorySet -> toDetail()
    is AuditProfileData -> toDetail()
    is AlarmJournalData -> toDetail()
    is AlarmJournalSFGroup -> toDetail()
    is ScriptedSFData -> toDetail()
    is RemoteEvent -> toDetail()
    is Array<*> -> {
        // 2D array
        if (firstOrNull()?.javaClass?.isArray == true) {
            Detail(
                title = TRANSACTION_GROUP_DATA,
                body =
                map { row ->
                    (row as Array<*>).contentToString()
                },
            )
        } else {
            Detail(
                title = "Java Array",
                body = map(Any?::toString),
            )
        }
    }

    else -> Detail(
        title = this::class.java.name,
        message = toString(),
    )
}

/**
 * @throws ClassNotFoundException
 */
fun ByteArray.deserializeStoreAndForward(): Serializable {
    return AliasingObjectInputStream(inputStream()) {
        put("com.inductiveautomation.ignition.gateway.audit.AuditProfileData", AuditProfileData::class.java)
        put("com.inductiveautomation.ignition.gateway.script.ialabs.IALabsDatasourceFunctions\$QuerySFData", ScriptedSFData::class.java)
        put("com.inductiveautomation.ignition.common.AbstractDataset", AbstractDataset::class.java)
        put("com.inductiveautomation.ignition.common.BasicDataset", BasicDataset::class.java)
        put("com.inductiveautomation.ignition.gateway.alarming.journal.remote.RemoteEvent", RemoteEvent::class.java)
        put("com.inductiveautomation.ignition.gateway.alarming.journal.DatabaseAlarmJournal\$AlarmJournalSFData", AlarmJournalData::class.java)
        put("com.inductiveautomation.ignition.gateway.alarming.journal.DatabaseAlarmJournal\$AlarmJournalSFGroup", AlarmJournalSFGroup::class.java)
    }.readObject() as Serializable
}

fun ScanclassHistorySet.toDetail(): Detail {
    return Detail(
        title = this::class.java.simpleName,
        body = map { historicalTagValue ->
            buildString {
                append(historicalTagValue.source.toStringFull())
                append(", ")
                append(historicalTagValue.typeClass.name)
                append(", ")
                append(historicalTagValue.value)
                append(", ")
                append(historicalTagValue.interpolationMode.name)
                append(", ")
                append(historicalTagValue.timestampSource.name)
            }
        },
        details = mapOf(
            "gatewayName" to gatewayName,
            "provider" to providerName,
            "setName" to setName,
            "execRate" to execRate.toString(),
            "execTime" to executionTime.time.toString(),
        ),
    )
}

fun BasicHistoricalRecord.toDetail(): Detail {
    return Detail(
        title = "BasicHistoricalRecord",
        message = "INSERT INTO $tablename",
        body = columns.map { column ->
            buildString {
                append(column.name).append(": ")
                (0..dataCount).joinTo(buffer = this, prefix = "(", postfix = ")") { row ->
                    column.getValue(row).toString()
                }
            }
        },
        details = mapOf(
            "quoteColumnNames" to quoteColumnNames().toString(),
        ),
    )
}
