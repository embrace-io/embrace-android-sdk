package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.SpansSinkImpl
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OpenTelemetryModuleImplTest {
    @Test
    fun testInitModuleImplDefaults() {
        val openTelemetryModule = OpenTelemetryModuleImpl(InitModuleImpl())
        assertTrue(openTelemetryModule.spansSink is SpansSinkImpl)
        assertTrue(openTelemetryModule.spansService is EmbraceSpansService)
        assertTrue(openTelemetryModule.currentSessionSpan is CurrentSessionSpanImpl)
    }
}
