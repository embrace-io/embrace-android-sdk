package io.embrace.android.embracesdk.minified

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MS = 10_000L

@RunWith(AndroidJUnit4::class)
internal class MinifiedSdkStartTest {

    @Test
    fun sdkStartsAndExportsSpans() {
        assertTrue(Embrace.isStarted)

        val span = awaitStartupSpan()
        assertNotNull("Span was not exported. Exported: ${RecordingSpanExporter.exportedSpans}", span)
        assertEquals(
            "Span was not exported by the expected opentelemetry-kotlin implementation.",
            if (BuildConfig.USE_KOTLIN_SDK) "kotlin" else null,
            span?.sdkLanguage,
        )
    }

    private fun awaitStartupSpan(): ExportedSpan? {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            RecordingSpanExporter.exportedSpans.find { it.name == MinifiedTestApplication.STARTUP_SPAN_NAME }?.let {
                return it
            }
            Thread.sleep(50)
        }
        return null
    }
}
