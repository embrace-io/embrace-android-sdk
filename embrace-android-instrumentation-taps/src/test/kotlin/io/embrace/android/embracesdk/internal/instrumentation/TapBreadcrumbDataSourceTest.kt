package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TapBreadcrumbDataSourceTest {

    private lateinit var source: TapDataSource
    private lateinit var writer: FakeSessionSpanWriter
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        writer = FakeSessionSpanWriter()
        clock = FakeClock()
        source = TapDataSource(
            FakeConfigService().breadcrumbBehavior,
            writer,
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
        with(writer.addedEvents.single()) {
            assertEquals(EmbType.Ux.Tap, schemaType.telemetryType)
            assertEquals(clock.now(), spanStartTimeMs)
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
        assertEquals(100, writer.addedEvents.size)
    }
}
