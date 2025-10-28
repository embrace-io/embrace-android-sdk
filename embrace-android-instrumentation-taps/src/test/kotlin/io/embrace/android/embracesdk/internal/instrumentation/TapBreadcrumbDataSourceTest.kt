package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TapBreadcrumbDataSourceTest {

    private lateinit var source: TapDataSource
    private lateinit var destination: FakeTelemetryDestination
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        destination = FakeTelemetryDestination()
        clock = FakeClock()
        source = TapDataSource(
            FakeConfigService().breadcrumbBehavior,
            destination,
            FakeEmbLogger(),
            clock,
        )
    }

    @Test
    fun `add breadcrumb`() {
        val point = Pair(126f, 309f)
        source.logComposeTap(
            point,
            "my-button-id",
        )
        with(destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.Tap, schemaType.telemetryType)
            assertEquals(clock.now(), startTimeMs)
            assertEquals(
                mapOf(
                    "view.name" to "my-button-id",
                    "tap.type" to "tap",
                    "tap.coords" to "126,309"
                ),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `limit not exceeded`() {
        val point = Pair(126f, 309f)
        repeat(150) { k ->
            source.logComposeTap(
                point,
                "my-button-$k",
            )
        }
        assertEquals(100, destination.addedEvents.size)
    }
}
