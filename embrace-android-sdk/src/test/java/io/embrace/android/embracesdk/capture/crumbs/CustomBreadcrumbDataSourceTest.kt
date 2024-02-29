package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class CustomBreadcrumbDataSourceTest {

    private lateinit var source: CustomBreadcrumbDataSource
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
        source = CustomBreadcrumbDataSource(
            FakeConfigService(),
            writer
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
            assertEquals("custom_breadcrumb", spanName)
            assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
            assertEquals(
                mapOf(
                    "message" to "Hello, world!"
                ),
                attributes
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
