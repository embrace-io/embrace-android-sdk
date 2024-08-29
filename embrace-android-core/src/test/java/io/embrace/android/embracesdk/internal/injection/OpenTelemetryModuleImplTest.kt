package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.opentelemetry.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.opentelemetry.EmbTracerProvider
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
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
