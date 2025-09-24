@file:Suppress("DEPRECATION")

package io.github.inductiveautomation.kindling.utils

import com.google.protobuf.GeneratedMessageV3
import com.inductiveautomation.ignition.gateway.alarming.journal.encoding.AlarmJournalProto
import com.inductiveautomation.ignition.gateway.history.encoding.GenericObjectProto
import com.inductiveautomation.ignition.gateway.history.encoding.HistoryDataProto
import com.inductiveautomation.ignition.gateway.storeforward.deprecated.BasicHistoricalRecord
import com.inductiveautomation.ignition.gateway.storeforward.deprecated.ScanclassHistorySet
import io.github.inductiveautomation.kindling.cache.AliasingObjectInputStream
import io.github.inductiveautomation.kindling.cache.hsql.model.AbstractDataset
import io.github.inductiveautomation.kindling.cache.hsql.model.AlarmJournalData
import io.github.inductiveautomation.kindling.cache.hsql.model.AlarmJournalSFGroup
import io.github.inductiveautomation.kindling.cache.hsql.model.AuditProfileData
import io.github.inductiveautomation.kindling.cache.hsql.model.BasicDataset
import io.github.inductiveautomation.kindling.cache.hsql.model.DefaultAuditRecord
import io.github.inductiveautomation.kindling.cache.hsql.model.RemoteEvent
import io.github.inductiveautomation.kindling.cache.hsql.model.ScriptedSFData
import io.github.inductiveautomation.kindling.core.Detail
import java.io.DataInputStream
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
        put($$"com.inductiveautomation.ignition.gateway.script.ialabs.IALabsDatasourceFunctions$QuerySFData", ScriptedSFData::class.java)
        put("com.inductiveautomation.ignition.common.AbstractDataset", AbstractDataset::class.java)
        put("com.inductiveautomation.ignition.common.BasicDataset", BasicDataset::class.java)
        put("com.inductiveautomation.ignition.gateway.alarming.journal.remote.RemoteEvent", RemoteEvent::class.java)
        put($$"com.inductiveautomation.ignition.gateway.alarming.journal.DatabaseAlarmJournal$AlarmJournalSFData", AlarmJournalData::class.java)
        put($$"com.inductiveautomation.ignition.gateway.alarming.journal.DatabaseAlarmJournal$AlarmJournalSFGroup", AlarmJournalSFGroup::class.java)
        put(
            "com.inductiveautomation.ignition.gateway.history.PackedHistoricalQualifiedValue",
            com.inductiveautomation.ignition.gateway.storeforward.deprecated.PackedHistoricalQualifiedValue::class.java,
        )
        put(
            "com.inductiveautomation.ignition.gateway.sqltags.model.BasicScanclassHistorySet",
            com.inductiveautomation.ignition.gateway.storeforward.deprecated.BasicScanclassHistorySet::class.java,
        )
        put(
            "com.inductiveautomation.ignition.gateway.audit.DefaultAuditRecord",
            DefaultAuditRecord::class.java,
        )
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

// 8.3

fun ByteArray.deserializeStoreAndForward(flavor: String): List<GeneratedMessageV3> {
    val parseFunction: (ByteArray) -> GeneratedMessageV3 = when (flavor) {
        "history_set" -> {
            HistoryDataProto.HistorySetPB::parseFrom
        }
        "alarm_journal_event" -> {
            AlarmJournalProto.JournalEventPB::parseFrom
        }
        else -> {
            GenericObjectProto.GenericObjectPB::parseFrom
        }
    }

    return buildList {
        DataInputStream(inputStream()).use { dis ->
            while (dis.available() > 0) {
                val length = dis.readInt()
                val objectBytes = ByteArray(length)
                dis.readFully(objectBytes)

                val byteData = objectBytes.unzip()

                add(parseFunction(byteData))
            }
        }
    }
}

fun GeneratedMessageV3.toDetail() = when (this) {
    is HistoryDataProto.HistorySetPB -> toDetail()
    is AlarmJournalProto.JournalEventPB -> toDetail()
    is GenericObjectProto.GenericObjectPB -> toDetail()
    else -> error("Unknown class: ${this::class.java.name}")
}

fun HistoryDataProto.HistorySetPB.toDetail(): Detail {
    return Detail(
        title = "Tag History Set of $tagValuesCount tag values",
        body = buildList<String> {
            add("System: $systemName")
            add("Provider: $providerName")
            add("Tag Group: $tagGroupName")
            add("Tag Values:")
            addAll(
                tagValuesList.map {
                    "Path: ${it.tagPath}\n${it.value}"
                }
            )
        }
    )
}

fun AlarmJournalProto.JournalEventPB.toDetail(): Detail {
    return Detail(
        title = "Alarm Journal Event",
        body = listOf(
            "Source: $source",
            "Display Path: $displayPath",
            "UUID: $uuid",
            "System Type: $systemType",
            "Target Journal: $targetJournal",
        )
    )
}

fun GenericObjectProto.GenericObjectPB.toDetail(): Detail {
    return Detail(
        title = "Generic Protobuf Object",
        body = listOf(this.toString()),
    )
}
