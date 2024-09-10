package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.assertions.assertExpectedAttributes
import io.embrace.android.embracesdk.assertions.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.internal.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.internal.opentelemetry.embSequenceId
import io.embrace.android.embracesdk.recordSession
import io.opentelemetry.api.common.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class SpanExporterTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `SDK can receive a SpanExporter`() {
        with(testRule) {
            val fakeSpanExporter = FakeSpanExporter()
            embrace.addSpanExporter(fakeSpanExporter)
            startSdk(context = harness.overriddenCoreModule.context)
            embrace.startSpan("test")?.stop()
            assertTrue(
                "Timed out waiting for the span to be exported: ${fakeSpanExporter.exportedSpans.map { it.name }}",
                fakeSpanExporter.awaitSpanExport(1)
            )
            // Verify that 2 spans have been logged - the exported ones and 1 private diagnostic traces
            assertEquals(2, harness.overriddenOpenTelemetryModule.spanSink.completedSpans().size)

            harness.recordSession {
                assertTrue(
                    "Timed out waiting for the span to be exported: ${fakeSpanExporter.exportedSpans.map { it.name }}",
                    fakeSpanExporter.awaitSpanExport(2)
                )
                // Verify that only 2 span is exported - the test one as well as the session span that ended
                assertEquals(2, fakeSpanExporter.exportedSpans.size)
                val exportedSpans = fakeSpanExporter.exportedSpans.associateBy { it.name }
                val testSpan = checkNotNull(exportedSpans["test"])
                testSpan.assertHasEmbraceAttribute(embSequenceId, "4")
                assertNotNull(testSpan.attributes.get(embProcessIdentifier.attributeKey))
                testSpan.resource.assertExpectedAttributes(
                    expectedServiceName = harness.overriddenOpenTelemetryModule.openTelemetryConfiguration.embraceSdkName,
                    expectedServiceVersion = harness.overriddenOpenTelemetryModule.openTelemetryConfiguration.embraceSdkVersion,
                    systemInfo = harness.overriddenInitModule.systemInfo
                )
                val sessionSpan = checkNotNull(exportedSpans["emb-session"])
                sessionSpan.assertHasEmbraceAttribute(embSequenceId, "1")
                assertNotNull(sessionSpan.attributes.get(embProcessIdentifier.attributeKey))
            }
        }
    }

    @Test
    fun `a SpanExporter added after initialization won't be used`() {
        with(testRule) {
            val fake = FakeInternalErrorService()
            harness.overriddenInitModule.logger.apply {
                internalErrorService = fake
            }

            val fakeSpanExporter = FakeSpanExporter()
            startSdk()
            embrace.addSpanExporter(fakeSpanExporter)

            harness.recordSession {
                embrace.startSpan("test")?.stop()

                Thread.sleep(3000)
            }
            assertTrue(fakeSpanExporter.exportedSpans.size == 0)
        }
    }
}
