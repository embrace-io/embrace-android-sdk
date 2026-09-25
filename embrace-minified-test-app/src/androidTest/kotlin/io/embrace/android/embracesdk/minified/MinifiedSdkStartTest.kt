package io.embrace.android.embracesdk.minified

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MS = 10_000L

@RunWith(AndroidJUnit4::class)
internal class MinifiedSdkStartTest {

    @Test
    fun sdkStartsAndExportsSpans() {
        assertTrue(Embrace.isStarted)

        val name = MinifiedTestApplication.STARTUP_SPAN_NAME
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!RecordingSpanExporter.exportedSpanNames.contains(name) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(
            "Span was not exported. Exported: ${RecordingSpanExporter.exportedSpanNames}",
            RecordingSpanExporter.exportedSpanNames.contains(name),
        )
    }
}
