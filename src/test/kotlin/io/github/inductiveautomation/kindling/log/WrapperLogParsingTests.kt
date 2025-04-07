package io.github.inductiveautomation.kindling.log

import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path
import kotlin.io.path.name

class WrapperLogParsingTests : FunSpec(
    {
        test("Simple case") {
            parse(
                """
            INFO   | jvm 1    | 2021/03/14 08:49:25 | I [t.h.q.PartitionManager        ] [07:49:25]: Ignition Created Tag history partition sqlt_data_1_20210314
            """,
            ).let { events ->
                events.shouldHaveSize(1)
                events.single().asClue { event ->
                    event.level shouldBe Level.INFO
                    event.logger shouldBe "t.h.q.PartitionManager"
                    event.message shouldBe "Ignition Created Tag history partition sqlt_data_1_20210314"
                    event.stacktrace.shouldBeEmpty()
                }
            }
        }

        test("Line with pipe in message") {
            parse(
                """
            INFO   | jvm 1    | 2022/02/03 15:46:57 | D [a.N.V.C.Agent                 ] [22:46:57]: SM z9hG4bKbIiG6tpOB|INVITE [InviteClientTransactionStateInit -> InviteClientTransactionStateCalling] setState
            """,
            ).let { events ->
                events.shouldHaveSize(1)
                events.single().asClue { event ->
                    event.level shouldBe Level.DEBUG
                    event.logger shouldBe "a.N.V.C.Agent"
                    event.message shouldBe "SM z9hG4bKbIiG6tpOB|INVITE [InviteClientTransactionStateInit -> InviteClientTransactionStateCalling] setState"
                    event.stacktrace.shouldBeEmpty()
                }
            }
        }

        test("Stacktrace below exception") {
            parse(
                """
            INFO   | jvm 1    | 2021/03/12 06:33:01 | W [S.S.TagHistoryDatasourceSink  ] [05:33:01]: There is a problem checking the tag history database tables during initialization of the store and forward engine which could prevent tag history data from being forwarded properly. Trying again in 60 seconds.
            INFO   | jvm 1    | 2021/03/12 06:33:01 | com.inductiveautomation.ignition.gateway.datasource.FaultedDatabaseConnectionException: The database connection 'IgnitionData' is FAULTED. See Gateway Status for details.
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.ignition.gateway.datasource.DatasourceManagerImpl.getConnectionImpl(DatasourceManagerImpl.java:201)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.ignition.gateway.datasource.DatasourceImpl.getConnection(DatasourceImpl.java:243)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.gateway.tags.history.storage.TagHistoryDatasourceSink.checkTables(TagHistoryDatasourceSink.java:1538)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.gateway.tags.history.storage.TagHistoryDatasourceSink.initialize(TagHistoryDatasourceSink.java:270)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at com.inductiveautomation.ignition.common.execution.impl.BasicExecutionEngine${'$'}ThrowableCatchingRunnable.run(BasicExecutionEngine.java:518)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.Executors${'$'}RunnableAdapter.call(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.FutureTask.run(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.ScheduledThreadPoolExecutor${'$'}ScheduledFutureTask.run(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(Unknown Source)
            INFO   | jvm 1    | 2021/03/12 06:33:01 | 	at java.base/java.lang.Thread.run(Unknown Source)
            """,
            ).let { events ->
                events.shouldHaveSize(1)
                events.single().asClue { event ->
                    event.message shouldBe "There is a problem checking the tag history database tables during initialization of the store and forward engine which could prevent tag history data from being forwarded properly. Trying again in 60 seconds."
                    event.stacktrace.shouldNotBeNull().shouldHaveSize(12).asClue { stack ->
                        stack.first() shouldBe "com.inductiveautomation.ignition.gateway.datasource.FaultedDatabaseConnectionException: The database connection 'IgnitionData' is FAULTED. See Gateway Status for details."
                        stack.last() shouldBe "\tat java.base/java.lang.Thread.run(Unknown Source)"
                    }
                }
            }
        }

        test("Multiple exceptions in a row") {
            parse(
                """
                INFO   | jvm 1    | 2022/01/26 15:00:50 | E [c.i.i.g.l.s.SingleConnectionDatasource] [15:00:50]: The following stack successfully received a connection. A new attempt was blocked for over 30000 ms tag-provider=default
                INFO   | jvm 1    | 2022/01/26 15:00:50 | java.lang.Throwable: null
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.localdb.sqlite.SingleConnectionDatasource.getConnection(SingleConnectionDatasource.java:58)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.localdb.sqlite.SQLiteDBManager${'$'}AutoBackupDaemon.run(SQLiteDBManager.java:666)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.common.execution.impl.BasicExecutionEngine${'$'}TrackedTask.run(BasicExecutionEngine.java:565)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.Executors${'$'}RunnableAdapter.call(Executors.java:511)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.FutureTask.runAndReset(FutureTask.java:308)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ScheduledThreadPoolExecutor${'$'}ScheduledFutureTask.access${'$'}301(ScheduledThreadPoolExecutor.java:180)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ScheduledThreadPoolExecutor${'$'}ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:294)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:624)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.lang.Thread.run(Thread.java:748)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | W [T.P.Config                    ] [15:00:50]: Error storing tag values. tag-provider=default
                INFO   | jvm 1    | 2022/01/26 15:00:50 | simpleorm.utils.SException${'$'}Jdbc: Opening com.inductiveautomation.ignition.gateway.localdb.sqlite.SingleConnectionDatasource@2fdc4e04
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at simpleorm.sessionjdbc.SSessionJdbc.innerOpen(SSessionJdbc.java:113)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.localdb.persistence.PersistenceSession.initialize(PersistenceSession.java:31)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.localdb.PersistenceInterfaceImpl.getSession(PersistenceInterfaceImpl.java:62)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.localdb.PersistenceInterfaceImpl.getSession(PersistenceInterfaceImpl.java:44)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.sqltags.tagproviders.internal.InternalTagStore.openIfNot(InternalTagStore.java:1303)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.sqltags.tagproviders.internal.InternalTagStore.internalStoreTagValues(InternalTagStore.java:1341)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.sqltags.tagproviders.internal.InternalTagStore.storeTagValues(InternalTagStore.java:1250)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.sqltags.providers.AbstractStoreBasedTagProvider.tagValuesChanged(AbstractStoreBasedTagProvider.java:2426)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.sqltags.scanclasses.SimpleExecutableScanClass${'$'}ScanClassTagEvaluationContext.processAndReset(SimpleExecutableScanClass.java:1165)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.sqltags.scanclasses.SimpleExecutableScanClass.run(SimpleExecutableScanClass.java:925)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.common.execution.impl.BasicExecutionEngine${'$'}SelfSchedulingRunner.run(BasicExecutionEngine.java:483)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.common.execution.impl.BasicExecutionEngine${'$'}TrackedTask.run(BasicExecutionEngine.java:565)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.Executors${'$'}RunnableAdapter.call(Executors.java:511)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.FutureTask.run(FutureTask.java:266)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ScheduledThreadPoolExecutor${'$'}ScheduledFutureTask.access${'$'}201(ScheduledThreadPoolExecutor.java:180)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ScheduledThreadPoolExecutor${'$'}ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:293)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:624)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at java.lang.Thread.run(Thread.java:748)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | Caused by: java.sql.SQLException: Connection is locked. Datasource only allows one connection at a time. More information was logged to the gateway console.
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at com.inductiveautomation.ignition.gateway.localdb.sqlite.SingleConnectionDatasource.getConnection(SingleConnectionDatasource.java:75)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	at simpleorm.sessionjdbc.SSessionJdbc.innerOpen(SSessionJdbc.java:111)
                INFO   | jvm 1    | 2022/01/26 15:00:50 | 	... 18 common frames omitted
                INFO   | jvm 1    | 2022/01/26 15:00:51 | Standard output
            """,
            ).let { events ->
                events.shouldHaveSize(3)
                events[0].asClue { event ->
                    event.level shouldBe Level.ERROR
                    event.logger shouldBe "c.i.i.g.l.s.SingleConnectionDatasource"
                    event.message shouldBe "The following stack successfully received a connection. A new attempt was blocked for over 30000 ms tag-provider=default"
                    event.stacktrace.shouldHaveSize(11).asClue { stack ->
                        stack.first() shouldBe "java.lang.Throwable: null"
                        stack.last() shouldBe "\tat java.lang.Thread.run(Thread.java:748)"
                    }
                }
                events[1].asClue { event ->
                    event.level shouldBe Level.WARN
                    event.logger shouldBe "T.P.Config"
                    event.stacktrace.shouldHaveSize(24).asClue { stack ->
                        stack.first() shouldBe "simpleorm.utils.SException\$Jdbc: Opening com.inductiveautomation.ignition.gateway.localdb.sqlite.SingleConnectionDatasource@2fdc4e04"
                        stack.last() shouldBe "\t... 18 common frames omitted"
                    }
                }
                events[2].asClue { event ->
                    event.logger shouldBe WrapperLogEvent.STDOUT
                    event.message shouldBe "Standard output"
                    event.stacktrace.shouldBeEmpty()
                }
            }
        }

        test("Wrapper file sorting test") {
            val input = listOf(
                "wrapper.log",
                "wrapper.log.1",
                "wrapper.log.2",
                "wrapper.log.3",
                "wrapper.log.4",
                "wrapper.log.5",
            ).map { Path(it) }.shuffled()
            input.sorted().map { it.name }.asClue { sorted ->
                sorted[0] shouldBe "wrapper.log"
                sorted[1] shouldBe "wrapper.log.1"
                sorted[2] shouldBe "wrapper.log.2"
                sorted[3] shouldBe "wrapper.log.3"
                sorted[4] shouldBe "wrapper.log.4"
                sorted[5] shouldBe "wrapper.log.5"
            }
        }
    },
) {
    companion object {
        fun parse(logs: String): List<WrapperLogEvent> {
            return WrapperLogPanel.parseLogs(logs.trimIndent().lineSequence())
        }
    }
}
