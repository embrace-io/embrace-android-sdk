package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.semconv.ServiceAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class OTelExportTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session span exported`() {
        val fakeSpanExporter = FakeOtelJavaSpanExporter()
        testRule.runTest(
            preSdkStartAction = {
                embrace.setResourceAttribute(ServiceAttributes.SERVICE_NAME.key, "my.app")
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
                    assertEquals("my.app", resource.attributes[ServiceAttributes.SERVICE_NAME])
                    assertEquals("foo", resource.attributes.asMap().filter { it.key.key == "test" }.values.single())
                }
            }
        )
    }

    @Test
    fun `a SpanExporter added after initialization won't be used`() {
        val fakeSpanExporter = FakeOtelJavaSpanExporter()

        testRule.runTest(
            testCaseAction = {
                embrace.addSpanExporter(fakeSpanExporter)
                recordSession {
                    embrace.startSpan("test")?.stop()
                }
            },
            assertAction = {
                assertNotNull(getSingleSessionEnvelope())
                assertTrue(fakeSpanExporter.exportedSpans.size == 0)
            }
        )
    }

    @Suppress("deprecation")
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
                    it.attributes.get(EmbType.System.Log.key.asOtelAttributeKey()) == EmbType.System.Log.value
                }
                with(log.single()) {
                    assertEquals("test message", body.asString())
                    assertEquals(logTimestampNanos, timestampEpochNanos)
                    assertEquals(logTimestampNanos, observedTimestampEpochNanos)
                    assertEquals("my.app", resource.attributes[ServiceAttributes.SERVICE_NAME])
                    assertEquals("foo", resource.attributes.asMap().filter { it.key.key == "test" }.values.single())
                }
            }
        )
    }
}
