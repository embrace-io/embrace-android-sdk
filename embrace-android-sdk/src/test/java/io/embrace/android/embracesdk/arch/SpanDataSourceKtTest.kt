package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.spans.SpanServiceImpl
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanDataSourceKtTest {

    @Test
    fun `start span`() {
        val initModule = FakeInitModule()
        val service = SpanServiceImpl(
            spanRepository = initModule.openTelemetryModule.spanRepository,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            embraceSpanFactory = EmbraceSpanFactoryImpl(
                tracer = initModule.openTelemetryModule.tracer,
                openTelemetryClock = initModule.openTelemetryClock,
                spanRepository = initModule.openTelemetryModule.spanRepository,
                serializer = initModule.jsonSerializer,
            )
        )
        service.initializeService(1500000000000)

        val data = StartSpanData(
            SchemaType.View("my-view"),
            1500000000000
        )
        data.assertIsType(EmbType.Ux.View)
        assertEquals("my-view", data.schemaType.attributes()["view.name"])

        val span = service.startSpanCapture("") { data }
        checkNotNull(span)
    }
}
