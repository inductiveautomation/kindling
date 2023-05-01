package io.github.inductiveautomation.kindling.thread

import io.github.inductiveautomation.kindling.thread.model.Thread.Companion.extractPool
import io.github.inductiveautomation.kindling.thread.model.ThreadDump
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe

class ThreadViewTests : FunSpec(
    {
        test("Thread JSON deserialization") {
            ThreadDump.fromStream(ThreadViewTests::class.java.getResourceAsStream("threadDump.json")!!)!!
                .asClue { (version, threads) ->
                    version shouldBe "Dev"
                    threads.size shouldBe 2
                }
        }
        test("Deadlock JSON deserialization") {
            ThreadDump.fromStream(ThreadViewTests::class.java.getResourceAsStream("deadlockThreadDump.json")!!)!!
                .asClue { (version, threads, deadlockIds) ->
                    version shouldBe "8.1.16.2022040511"
                    threads.size shouldBe 5
                    deadlockIds.size shouldBe 3
                }
        }
        context("Legacy parsing") {
            test("From webpage") {
                ThreadDump.fromStream(ThreadViewTests::class.java.getResourceAsStream("legacyWebThreadDump.txt")!!)!!
                    .asClue { (version, threads) ->
                        version shouldBe "7.9.14 (b2020042813)"
                        threads.size shouldBe 4
                    }
            }
            test("From Auto-Generated Deadlock") {
                ThreadDump.fromStream(ThreadViewTests::class.java.getResourceAsStream("legacyDeadlockThreadDump.txt")!!)!!
                    .asClue { (version, threads, deadlockIds) ->
                        version shouldBe "8.1.7 (b2021060314)"
                        threads.size shouldBe 5
                        deadlockIds.size shouldBe 3
                    }
            }
            test("From scripting") {
                ThreadDump.fromStream(ThreadViewTests::class.java.getResourceAsStream("legacyScriptThreadDump.txt")!!)!!
                    .asClue { (version, threads) ->
                        version shouldBe "8.1.1 (b2020120808)"
                        threads.size shouldBe 3
                    }
            }
        }

        test("Thread Pool Parsing Tests") {
            forAll(
                row("gateway-logging-sqlite-appender", null),
                row("gateway-performance-metric-history-1", "gateway-performance-metric-history"),
                row("gateway-performance-metric-history-2", "gateway-performance-metric-history"),
                row("gateway-shared-exec-engine-11", "gateway-shared-exec-engine"),
                row("gateway-storeforward-pipeline[postgres]-engine[PrimarySFEngine]", null),
                row("gateway.tags.subscriptionmodel-1", "gateway.tags.subscriptionmodel"),
                row("HSQLDB Timer @32c91059", null),
                row("HttpClient-1-SelectorManager", null),
                row("HttpClient@25d4330e-1129", "HttpClient@25d4330e"),
                row("HttpClient@25d4330e-1315", "HttpClient@25d4330e"),
                row("milo-netty-event-loop-0", "milo-netty-event-loop"),
                row("opc-ua-executor-18", "opc-ua-executor"),
                row("opc-ua-executor-19", "opc-ua-executor"),
                row("Session-Scheduler-782a4fff-1", "Session-Scheduler-782a4fff"),
                row("webserver-1114", "webserver"),
                row(
                    // maybe someday
                    "webserver-43-acceptor-0@25cd7918-ServerConnector@1d7f7be7{SSL, (ssl, http/1.1)}{0.0.0.0:8060}",
                    null,
                ),
                row("AsyncSocketIOSession[I/O]-1", "AsyncSocketIOSession[I/O]"),
            ) { name, pool ->
                extractPool(name) shouldBe pool
            }
        }
    },
)
