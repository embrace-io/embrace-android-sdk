package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import org.junit.Assert.assertEquals
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
            fakeSpanExporter.awaitSpanExport()
            val exportedSpans = fakeSpanExporter.exportedSpans.filter { it.name == "emb-sdk-init" }
            assertEquals(1, exportedSpans.size)
            assertEquals(1, exportedSpans[0].attributes.asMap().keys.filter { it.key == "emb.sequence_id" }.size)
        }
    }
}
