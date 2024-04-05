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
            assertTrue(
                "Timed out waiting for the span to be exported: ${fakeSpanExporter.exportedSpans.map { it.name }}",
                fakeSpanExporter.awaitSpanExport(1)
            )
            val exportedSpans = fakeSpanExporter.exportedSpans.filter { it.name == "emb-sdk-init" }
            assertEquals(1, exportedSpans.size)
            val exportedSpan = exportedSpans[0]
            exportedSpan.assertHasEmbraceAttribute(embSequenceId, "2")
            exportedSpan.assertHasEmbraceAttribute(embProcessIdentifier, harness.initModule.processIdentifier)
            exportedSpan.resource.assertExpectedAttributes(
                expectedServiceName = harness.openTelemetryModule.openTelemetryConfiguration.embraceServiceName,
                expectedServiceVersion = harness.openTelemetryModule.openTelemetryConfiguration.embraceVersionName,
                systemInfo = harness.initModule.systemInfo
            )
        }
    }
}
