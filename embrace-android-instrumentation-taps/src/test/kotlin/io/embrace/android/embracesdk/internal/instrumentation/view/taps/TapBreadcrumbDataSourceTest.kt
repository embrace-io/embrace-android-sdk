package io.embrace.android.embracesdk.internal.instrumentation.view.taps

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class TapBreadcrumbDataSourceTest {

    private lateinit var source: TapDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        source = TapDataSource(args)
    }

    @Test
    fun `add breadcrumb`() {
        val point = Pair(126f, 309f)
        source.logComposeTap(
            point,
            "my-button-id",
        )
        with(args.destination.addedEvents.single()) {
            assertEquals(EmbType.Ux.Tap, schemaType.telemetryType)
            assertEquals(args.clock.now(), startTimeMs)
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
        assertEquals(100, args.destination.addedEvents.size)
    }
}
