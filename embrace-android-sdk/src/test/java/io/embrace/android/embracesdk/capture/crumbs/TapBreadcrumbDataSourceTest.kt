package io.embrace.android.embracesdk.capture.crumbs

import android.util.Pair
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.TapBreadcrumb
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal class TapBreadcrumbDataSourceTest {

    private lateinit var source: TapBreadcrumbDataSource
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
        source = TapBreadcrumbDataSource(
            FakeConfigService().breadcrumbBehavior,
            writer,
            InternalEmbraceLogger(),
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
            Assert.assertEquals("emb-ui-tap", schemaType.defaultName)
            Assert.assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
            Assert.assertEquals(
                mapOf(
                    EmbType.Ux.Tap.toEmbraceKeyValuePair(),
                    "view.name" to "my-button-id",
                    "tap.type" to "tap",
                    "tap.coords" to "0,0"
                ),
                schemaType.attributes()
            )
        }
    }
}
