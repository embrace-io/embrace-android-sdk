package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class BreadcrumbDataSourceTest {

    private lateinit var source: BreadcrumbDataSource
    private lateinit var destination: FakeTelemetryDestination

    @Before
    fun setUp() {
        destination = FakeTelemetryDestination()
        source = BreadcrumbDataSource(
            FakeBreadcrumbBehavior(),
            destination,
            FakeEmbLogger()
        )
    }

    @Test
    fun `add invalid breadcrumb`() {
        source.logCustom("", 0)
        assertEquals(0, destination.addedEvents.size)
    }

    @Test
    fun `add breadcrumb`() {
        source.logCustom("Hello, world!", 15000000000)
        with(destination.addedEvents.single()) {
            assertEquals(EmbType.System.Breadcrumb, schemaType.telemetryType)
            assertEquals(15000000000, startTimeMs)
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
        assertEquals(100, destination.addedEvents.size)
    }
}
