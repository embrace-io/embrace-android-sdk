package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.opentelemetry.assertExpectedAttributes
import io.embrace.android.embracesdk.opentelemetry.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.opentelemetry.embSequenceId
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
            embrace.start(harness.fakeCoreModule.context)
            embrace.startSpan("test")?.stop()
            assertTrue(
                "Timed out waiting for the span to be exported: ${fakeSpanExporter.exportedSpans.map { it.name }}",
                fakeSpanExporter.awaitSpanExport(1)
            )
            // Verify that 2 spans have been logged - one private and one non-private
            assertEquals(2, harness.openTelemetryModule.spanSink.completedSpans().size)

            // Verify that only 1 span is exported - the non-private one
            val exportedSpan = fakeSpanExporter.exportedSpans.single()
            assertEquals("test", exportedSpan.name)
            exportedSpan.assertHasEmbraceAttribute(embSequenceId, "3")
            exportedSpan.assertHasEmbraceAttribute(embProcessIdentifier, harness.initModule.processIdentifier)
            exportedSpan.resource.assertExpectedAttributes(
                expectedServiceName = harness.openTelemetryModule.openTelemetryConfiguration.embraceServiceName,
                expectedServiceVersion = harness.openTelemetryModule.openTelemetryConfiguration.embraceVersionName,
                systemInfo = harness.initModule.systemInfo
            )

        }
    }
}
