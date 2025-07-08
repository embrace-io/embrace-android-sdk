package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.otel.impl.EmbOtelJavaOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OpenTelemetryModuleImplTest {
    @OptIn(ExperimentalApi::class)
    @Test
    fun testInitModuleImplDefaults() {
        val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(InitModuleImpl())
        assertTrue(openTelemetryModule.spanSink is SpanSinkImpl)
        assertTrue(openTelemetryModule.spanService is EmbraceSpanService)
        assertTrue(openTelemetryModule.currentSessionSpan is CurrentSessionSpanImpl)
        assertTrue(openTelemetryModule.logSink is LogSinkImpl)
        assertTrue(openTelemetryModule.openTelemetryJava is EmbOtelJavaOpenTelemetry)
        assertNotNull(openTelemetryModule.logger)
        assertNotNull(openTelemetryModule.sdkTracer)
        assertNotNull(openTelemetryModule.openTelemetryKotlin)
    }
}
