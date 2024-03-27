package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.spans.SpanServiceImpl
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanDataSourceKtTest {

    @Test
    fun `start span`() {
        val initModule = FakeInitModule()
        val service = SpanServiceImpl(
            openTelemetryClock = initModule.openTelemetryClock,
            spanRepository = initModule.openTelemetryModule.spanRepository,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            tracer = initModule.openTelemetryModule.tracer
        )
        service.initializeService(1500000000000)

        val data = StartSpanData(
            telemetryType = EmbType.Ux.View,
            schemaType = SchemaType.ViewBreadcrumb("my-view"),
            spanStartTimeMs = 1500000000000
        )
        data.assertIsType(EmbType.Ux.View)
        assertEquals("my-view", data.attributes["view.name"])

        val span = service.startSpanCapture("") { data }
        checkNotNull(span)
    }
}
