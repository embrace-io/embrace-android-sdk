package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.otel.java.addJavaLogRecordExporter
import io.embrace.android.embracesdk.otel.java.addJavaLogRecordProcessor
import io.embrace.android.embracesdk.otel.java.addJavaSpanExporter
import io.embrace.android.embracesdk.otel.java.addJavaSpanProcessor
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class OTelExportTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session span exported to user-supplied exporter`() {
        val fakeSpanExporter = FakeSpanExporter()
        testRule.runTest(
            preSdkStartAction = {
                embrace.setResourceAttribute(ServiceAttributes.SERVICE_NAME, "my.app")
                embrace.setResourceAttribute("test", "foo")
                embrace.addSpanExporter(fakeSpanExporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan("test-span").stop()
                }
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
                val exportedSpans = fakeSpanExporter.exportedSpans.associateBy { it.name }
                assertNotNull(exportedSpans["emb-session"])
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "test-span" }
                with(span.single()) {
                    assertEquals("my.app", resource.attributes.toStringMap()[ServiceAttributes.SERVICE_NAME])
                    assertEquals("foo", resource.attributes.asMap().filter { it.key.key == "test" }.values.single())
                }
            }
        )
    }

    @Test
    fun `a SpanExporter added after initialization won't be used`() {
        val fakeSpanExporter = FakeSpanExporter()

        testRule.runTest(
            testCaseAction = {
                embrace.addSpanExporter(fakeSpanExporter)
                recordSession {
                    embrace.startSpan("test").stop()
                }
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
                assertTrue(fakeSpanExporter.exportedSpans.isEmpty())
            }
        )
    }

    @Test
    fun `log record exported to user-supplied LogRecordExporter`() {
        var logTimestampNanos = 0L

        testRule.runTest(
            preSdkStartAction = {
                embrace.setResourceAttribute(ServiceAttributes.SERVICE_NAME, "my.app")
                embrace.setResourceAttribute("test", "foo")
            },
            testCaseAction = {
                recordSession {
                    logTimestampNanos = clock.now().millisToNanos()
                    embrace.logMessage("test message", Severity.INFO)
                }
            },
            otelExportAssertion = {
                val log = awaitLogs(1) {
                    it.attributes.toStringMap().containsKey(EmbType.System.Log.key.name)
                }
                with(log.single()) {
                    assertEquals("test message", body.asString())
                    assertEquals(logTimestampNanos, timestampEpochNanos)
                    assertEquals(logTimestampNanos, observedTimestampEpochNanos)
                    assertEquals("my.app", resource.attributes.toStringMap()[ServiceAttributes.SERVICE_NAME])
                    assertEquals("foo", resource.attributes.asMap().filter { it.key.key == "test" }.values.single())
                }
            }
        )
    }

    @Test
    fun `span exported to user-supplied SpanProcessor`() {
        val processor = FakeSpanProcessor()
        val spanName = "test-processor-span"

        testRule.runTest(
            preSdkStartAction = {
                embrace.addSpanProcessor(processor)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan(spanName).stop()
                }
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                assertTrue(processor.startedSpanNames.contains(spanName))
                assertTrue(processor.endedSpanNames.contains(spanName))
            }
        )
    }

    @Test
    fun `log record exported to user-supplied LogRecordProcessor`() {
        val processor = FakeLogRecordProcessor()
        val logMessage = "test-processor-log"

        testRule.runTest(
            preSdkStartAction = {
                embrace.addLogRecordProcessor(processor)
            },
            testCaseAction = {
                recordSession {
                    embrace.logMessage(logMessage, Severity.INFO)
                }
            },
            otelExportAssertion = {
                awaitLogs(1) { it.body.asString() == logMessage }
                assertTrue(processor.processedLogBodies.contains(logMessage))
            }
        )
    }

    @Test
    fun `span exported to user-supplied SpanExporter using Java API`() {
        val exporter = FakeOtelJavaSpanExporter()
        val spanName = "test-java-exporter-span"

        testRule.runTest(
            preSdkStartAction = {
                embrace.addJavaSpanExporter(exporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan(spanName).stop()
                }
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                exporter.awaitSpanExport(1)
                assertTrue(exporter.exportedSpans.any { it.name == spanName })
            }
        )
    }

    @Test
    fun `log record exported to user-supplied LogRecordExporter using Java API`() {
        val exporter = FakeOtelJavaLogRecordExporter()
        val logMessage = "test-java-exporter-log"

        testRule.runTest(
            preSdkStartAction = {
                embrace.addJavaLogRecordExporter(exporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.logMessage(logMessage, Severity.INFO)
                }
            },
            otelExportAssertion = {
                awaitLogs(1) { it.body.asString() == logMessage }
                assertTrue(exporter.exportedLogs.any { it.body.asString() == logMessage })
            }
        )
    }

    @Test
    fun `span exported to user-supplied SpanProcessor using Java API`() {
        val processor = FakeOtelJavaSpanProcessor()
        val spanName = "test-java-processor-span"

        testRule.runTest(
            preSdkStartAction = {
                embrace.addJavaSpanProcessor(processor)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan(spanName).stop()
                }
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                assertTrue(processor.startedSpanNames.contains(spanName))
                assertTrue(processor.endedSpanNames.contains(spanName))
            }
        )
    }

    @Test
    fun `log record exported to user-supplied LogRecordProcessor using Java API`() {
        val processor = FakeOtelJavaLogRecordProcessor()

        testRule.runTest(
            preSdkStartAction = {
                embrace.addJavaLogRecordProcessor(processor)
            },
            testCaseAction = {
                recordSession {
                    embrace.logMessage("test-java-processor-log", Severity.INFO)
                }
            },
            otelExportAssertion = {
                awaitLogs(1) { it.body.asString() == "test-java-processor-log" }
                assertTrue(processor.logCount > 0)
            }
        )
    }

    @Test
    fun `add opentelemetry-kotlin exporters`() {
        val logRecordExporter = FakeLogRecordExporter()
        val spanExporter = FakeSpanExporter()
        val spanName = "test"
        val logMessage = "Hello, World!"

        testRule.runTest(
            preSdkStartAction = {
                embrace.addLogRecordExporter(logRecordExporter)
                embrace.addSpanExporter(spanExporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan(spanName).stop()
                    embrace.logMessage(logMessage, Severity.INFO)
                }
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                awaitLogs(1) { it.body.asString() == logMessage }
                spanExporter.exportedSpans.single { it.name == spanName }
                logRecordExporter.exportedLogs.single { it.body == logMessage }
            }
        )
    }

    @Test
    fun `test otel export without sending data to embrace`() {
        val logRecordExporter = FakeLogRecordExporter()
        val spanExporter = FakeSpanExporter()
        val spanName = "test"
        val logMessage = "Hello, World!"

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                project = FakeProjectConfig(appId = null)
            ),
            preSdkStartAction = {
                embrace.addLogRecordExporter(logRecordExporter)
                embrace.addSpanExporter(spanExporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan(spanName).stop()
                    embrace.logMessage(logMessage, Severity.INFO)
                }
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
                assertEquals(0, getLogEnvelopes(0).size)
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                awaitLogs(1) { it.body.asString() == logMessage }
            }
        )
    }

    @Test
    fun `service name added as resource attribute`() {
        val spanExporter = FakeSpanExporter()
        val packageName = "default-package-name"

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                project = FakeProjectConfig(packageName = packageName)
            ),
            preSdkStartAction = {
                embrace.addSpanExporter(spanExporter)
            },
            testCaseAction = {
                recordSession { }
            },
            assertAction = {
                val span = spanExporter.exportedSpans.first()
                assertEquals(packageName, span.resource.attributes[ServiceAttributes.SERVICE_NAME])

            },
            otelExportAssertion = {
                awaitSpans(1) {
                    it.name == "emb-session" && it.resource.attributes.toStringMap()[ServiceAttributes.SERVICE_NAME] == packageName
                }
            }
        )
    }

    @Test
    fun `span processors are invoked in order they were added`() {
        val startCalls = mutableListOf<String>()
        val endCalls = mutableListOf<String>()
        val spanName = "test-order-span"
        val processor1 = FakeSpanProcessor(
            onStartAction = {
                if (it.name == spanName) {
                    startCalls.add("a")
                }
            },
            onEndAction = {
                if (it.name == spanName) {
                    endCalls.add("a")
                }
            }
        )
        val processor2 = FakeSpanProcessor(
            onStartAction = {
                if (it.name == spanName) {
                    startCalls.add("b")
                }
            },
            onEndAction = {
                if (it.name == spanName) {
                    endCalls.add("b")
                }
            }
        )
        val processor3 = FakeSpanProcessor(
            onStartAction = {
                if (it.name == spanName) {
                    startCalls.add("c")
                }
            },
            onEndAction = {
                if (it.name == spanName) {
                    endCalls.add("c")
                }
            }
        )

        testRule.runTest(
            preSdkStartAction = {
                embrace.addSpanProcessor(processor1)
                embrace.addSpanProcessor(processor2)
                embrace.addSpanProcessor(processor3)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan(spanName).stop()
                }
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                assertEquals(listOf("a", "b", "c"), startCalls)
                assertEquals(listOf("a", "b", "c"), endCalls)
            }
        )
    }


    @Test
    fun `log record processors are invoked in order they were added`() {
        val msg = "Hello, world!"
        val calls = mutableListOf<String>()
        val processor1 = FakeLogRecordProcessor(
            onEmitAction = {
                calls.add("a")
            }
        )
        val processor2 = FakeLogRecordProcessor(
            onEmitAction = {
                calls.add("b")
            }
        )
        val processor3 = FakeLogRecordProcessor(
            onEmitAction = {
                calls.add("c")
            }
        )
        testRule.runTest(
            preSdkStartAction = {
                embrace.addLogRecordProcessor(processor1)
                embrace.addLogRecordProcessor(processor2)
                embrace.addLogRecordProcessor(processor3)
            },
            testCaseAction = {
                recordSession {
                    embrace.logInfo(msg)
                }
            },
            otelExportAssertion = {
                awaitLogs(1) { it.body.asString() == msg }
                assertEquals(listOf("a", "b", "c"), calls)
            }
        )
    }
}
