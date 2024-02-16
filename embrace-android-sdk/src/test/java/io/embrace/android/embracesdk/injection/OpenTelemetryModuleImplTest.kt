package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OpenTelemetryModuleImplTest {
    @Test
    fun testInitModuleImplDefaults() {
        val openTelemetryModule = OpenTelemetryModuleImpl(InitModuleImpl())
        assertTrue(openTelemetryModule.spanSink is SpanSinkImpl)
        assertTrue(openTelemetryModule.spanService is EmbraceSpanService)
        assertTrue(openTelemetryModule.currentSessionSpan is CurrentSessionSpanImpl)
    }
}
