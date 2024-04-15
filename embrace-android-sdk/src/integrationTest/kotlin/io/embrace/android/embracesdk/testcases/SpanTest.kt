package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.opentelemetry.assertExpectedAttributes
import io.embrace.android.embracesdk.opentelemetry.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.opentelemetry.embSequenceId
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class SpanTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `SDK can receive a SpanExporter`() {
        with(testRule) {
            val fakeSpanExporter = FakeSpanExporter()
            embrace.addSpanExporter(fakeSpanExporter)
            embrace.start(harness.overriddenCoreModule.context)
            embrace.startSpan("test")?.stop()
            assertTrue(
                "Timed out waiting for the span to be exported: ${fakeSpanExporter.exportedSpans.map { it.name }}",
                fakeSpanExporter.awaitSpanExport(1)
            )
            // Verify that 2 spans have been logged - the exported ones and the private not-exported emb-sdk-init
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
                testSpan.assertHasEmbraceAttribute(embSequenceId, "3")
                testSpan.assertHasEmbraceAttribute(embProcessIdentifier, harness.overriddenInitModule.processIdentifier)
                testSpan.resource.assertExpectedAttributes(
                    expectedServiceName = harness.overriddenOpenTelemetryModule.openTelemetryConfiguration.embraceServiceName,
                    expectedServiceVersion = harness.overriddenOpenTelemetryModule.openTelemetryConfiguration.embraceVersionName,
                    systemInfo = harness.overriddenInitModule.systemInfo
                )
                val sessionSpan = checkNotNull(exportedSpans["emb-session"])
                sessionSpan.assertHasEmbraceAttribute(embSequenceId, "1")
                testSpan.assertHasEmbraceAttribute(embProcessIdentifier, harness.overriddenInitModule.processIdentifier)
            }
        }
    }
}
