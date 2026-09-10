package io.embrace.android.embracesdk.internal.config.instrumented.schema

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import org.junit.Assert.assertEquals
import org.junit.Test

internal class WebViewFragmentCaptureTest {

    @Test
    fun `the default is keep`() {
        assertEquals(
            WebViewFragmentCapture.KEEP,
            InstrumentedConfigImpl.enabledFeatures.getWebViewBreadcrumbFragmentCapture(),
        )
    }

    @Test
    fun `constant names are stable`() {
        // the Gradle plugin selects a constant by name, so renaming one silently breaks
        // instrumentation for anyone who has set the config value
        assertEquals(
            listOf("KEEP", "REDACT", "REMOVE"),
            WebViewFragmentCapture.entries.map { it.name },
        )
    }
}
