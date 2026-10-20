package io.embrace.android.embracesdk.internal.instrumentation.view.taps

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal
import io.embrace.android.embracesdk.internal.config.resolved.BreadcrumbConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.semconv.EmbTapAttributes
import io.embrace.android.embracesdk.semconv.EmbViewAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TapBreadcrumbDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: TapDataSource

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(
            configService = FakeConfigService(
                config = EmbraceConfig(breadcrumb = { BreadcrumbConfig(captureViewClickCoordinates = { true }) }),
            ),
        )

        // enabling the data source registers its handler on the event bus
        dataSource = TapDataSource(args).apply { onDataCaptureEnabled() }
    }

    @Test
    fun `taps are not recorded once data capture is disabled`() {
        dataSource.onDataCaptureDisabled()
        args.eventBus.emit(TapSignal.KEY, TapSignal("my-button-id", 126f, 309f))
        assertEquals(emptyList<Any>(), args.destination.addedEvents)
    }

    @Test
    fun `add breadcrumb`() {
        args.eventBus.emit(TapSignal.KEY, TapSignal("my-button-id", 126f, 309f))

        with(args.destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.Tap, schemaType.telemetryType)
            assertEquals(args.clock.now(), startTimeMs)
            assertEquals(
                mapOf(
                    EmbViewAttributes.VIEW_NAME to "my-button-id",
                    EmbTapAttributes.TAP_TYPE to "tap",
                    EmbTapAttributes.TAP_COORDS to "126,309",
                ),
                schemaType.attributes(),
            )
        }
    }

    @Test
    fun `limit not exceeded`() {
        repeat(150) { k ->
            args.eventBus.emit(TapSignal.KEY, TapSignal("my-button-$k", 126f, 309f))
        }
        assertEquals(100, args.destination.addedEvents.size)
    }
}
