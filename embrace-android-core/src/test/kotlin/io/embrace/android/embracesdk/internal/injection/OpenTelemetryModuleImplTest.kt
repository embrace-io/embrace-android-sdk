package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpanImpl
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OpenTelemetryModuleImplTest {
    @Test
    fun testInitModuleImplDefaults() {
        val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(InitModuleImpl())
        assertTrue(openTelemetryModule.spanSink is SpanSinkImpl)
        assertTrue(openTelemetryModule.spanService is EmbraceSpanService)
        assertTrue(openTelemetryModule.currentSessionPartSpan is CurrentSessionPartSpanImpl)
        assertTrue(openTelemetryModule.logSink is LogSinkImpl)
    }
}
