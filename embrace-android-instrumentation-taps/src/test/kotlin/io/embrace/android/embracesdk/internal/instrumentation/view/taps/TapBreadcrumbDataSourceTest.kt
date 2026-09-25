package io.embrace.android.embracesdk.internal.instrumentation.view.taps

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.ui.TapSignal
import io.embrace.android.embracesdk.semconv.EmbTapAttributes
import io.embrace.android.embracesdk.semconv.EmbViewAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class TapBreadcrumbDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())

        // constructing the data source registers its handler on the event bus
        TapDataSource(args)
    }

    @Test
    fun `add breadcrumb`() {
        args.eventBus.emit(TapSignal("my-button-id", 126f, 309f))

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
            args.eventBus.emit(TapSignal("my-button-$k", 126f, 309f))
        }
        assertEquals(100, args.destination.addedEvents.size)
    }
}
