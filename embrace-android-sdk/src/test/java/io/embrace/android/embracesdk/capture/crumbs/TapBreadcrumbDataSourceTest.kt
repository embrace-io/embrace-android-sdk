package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.TapBreadcrumb
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TapBreadcrumbDataSourceTest {

    private lateinit var source: TapDataSource
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
        source = TapDataSource(
            FakeConfigService().breadcrumbBehavior,
            writer,
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `add breadcrumb`() {
        val point = Pair(126f, 309f)
        source.logTap(
            point,
            "my-button-id",
            15000000000,
            TapBreadcrumb.TapBreadcrumbType.TAP
        )
        with(writer.addedEvents.single()) {
            assertEquals(EmbType.Ux.Tap, schemaType.telemetryType)
            assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
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
            source.logTap(
                point,
                "my-button-$k",
                15000000000,
                TapBreadcrumb.TapBreadcrumbType.TAP
            )
        }
        Assert.assertEquals(100, writer.addedEvents.size)
    }
}
