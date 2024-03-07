package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.spans.SpanServiceImpl
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanDataSourceKtTest {

    @Test
    fun `start span`() {
        val initModule = FakeInitModule(FakeClock())
        val service = SpanServiceImpl(
            spanRepository = initModule.openTelemetryModule.spanRepository,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            tracer = initModule.openTelemetryModule.tracer
        )
        service.initializeService(1500000000000)

        val data = StartSpanData(
            SchemaType.ViewBreadcrumb("my-view"),
            1500000000000
        )
        assertEquals("ux.view", data.attributes["emb.type"])
        assertEquals("my-view", data.attributes["view.name"])

        val span = service.startSpanCapture("") { data }
        checkNotNull(span)
    }
}
