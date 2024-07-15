package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class BreadcrumbDataSourceTest {

    private lateinit var source: BreadcrumbDataSource
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
        source = BreadcrumbDataSource(
            FakeConfigService().breadcrumbBehavior,
            writer,
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `add invalid breadcrumb`() {
        source.logCustom("", 0)
        assertEquals(0, writer.addedEvents.size)
    }

    @Test
    fun `add breadcrumb`() {
        source.logCustom("Hello, world!", 15000000000)
        with(writer.addedEvents.single()) {
            assertIsType(EmbType.System.Breadcrumb)
            assertEquals(15000000000, spanStartTimeMs)
            assertEquals(
                mapOf("message" to "Hello, world!"),
                schemaType.attributes()
            )
        }
    }

    @Test
    fun `limit not exceeded`() {
        repeat(150) { k ->
            source.logCustom("Crumb #$k", 15000000000)
        }
        assertEquals(100, writer.addedEvents.size)
    }
}
