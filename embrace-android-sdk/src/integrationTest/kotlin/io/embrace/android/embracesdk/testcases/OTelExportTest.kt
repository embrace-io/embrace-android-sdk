package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.semconv.ServiceAttributes
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
    fun `session span exported`() {
        val fakeSpanExporter = FakeSpanExporter()
        testRule.runTest(
            preSdkStartAction = {
                embrace.setResourceAttribute(ServiceAttributes.SERVICE_NAME, "my.app")
                embrace.setResourceAttribute("test", "foo")
                embrace.addSpanExporter(fakeSpanExporter)
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan("test-span")?.stop()
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
                    embrace.startSpan("test")?.stop()
                }
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
                assertTrue(fakeSpanExporter.exportedSpans.isEmpty())
            }
        )
    }

    @Test
    fun `SDK can receive a LogRecordExporter`() {
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
                    embrace.startSpan(spanName)?.stop()
                    embrace.logMessage(logMessage, Severity.INFO)
                }
            },
            otelExportAssertion = {
                awaitSpans(1) { it.name == spanName }
                awaitLogs(1) { it.body.asString() == logMessage }
            }
        )
        spanExporter.exportedSpans.single { it.name == spanName }
        logRecordExporter.exportedLogs.single { it.body == logMessage }
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
                    embrace.startSpan(spanName)?.stop()
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
}
