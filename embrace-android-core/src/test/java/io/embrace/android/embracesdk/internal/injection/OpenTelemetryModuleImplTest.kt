package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.otel.impl.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.impl.EmbTracerProvider
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OpenTelemetryModuleImplTest {
    @Test
    fun testInitModuleImplDefaults() {
        val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(InitModuleImpl())
        assertTrue(openTelemetryModule.spanSink is SpanSinkImpl)
        assertTrue(openTelemetryModule.spanService is EmbraceSpanService)
        assertTrue(openTelemetryModule.currentSessionSpan is CurrentSessionSpanImpl)
        assertTrue(openTelemetryModule.logSink is LogSinkImpl)
        assertTrue(openTelemetryModule.externalOpenTelemetry is EmbOpenTelemetry)
        assertTrue(openTelemetryModule.externalTracerProvider is EmbTracerProvider)
        assertNotNull(openTelemetryModule.logger)
        assertNotNull(openTelemetryModule.sdkTracer)
    }
}
