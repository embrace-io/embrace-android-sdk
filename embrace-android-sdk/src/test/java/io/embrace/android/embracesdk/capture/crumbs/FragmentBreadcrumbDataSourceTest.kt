package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class FragmentBreadcrumbDataSourceTest {

    private lateinit var configService: FakeConfigService
    private lateinit var clock: FakeClock
    private lateinit var spanService: FakeSpanService
    private lateinit var dataSource: FragmentBreadcrumbDataSource

    @Before
    fun setUp() {
        configService = FakeConfigService()
        clock = FakeClock()
        spanService = FakeSpanService()
        dataSource = FragmentBreadcrumbDataSource(
            configService.breadcrumbBehavior,
            clock,
            spanService,
            InternalEmbraceLogger(),
        )
    }

    @Test
    fun `fragment with start`() {
        dataSource.startFragment("my_fragment")

        val span = spanService.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertTrue(span.isRecording)
        assertEquals(
            mapOf(
                "view.name" to "my_fragment",
                EmbType.Ux.View.toOTelKeyValuePair(),
            ),
            span.attributes
        )
    }

    @Test
    fun `fragment with start and end`() {
        dataSource.startFragment("my_fragment")
        clock.tick(30000)
        dataSource.endFragment("my_fragment")

        val span = spanService.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertFalse(span.isRecording)
        assertEquals(
            mapOf(
                "view.name" to "my_fragment",
                EmbType.Ux.View.toOTelKeyValuePair()
            ),
            span.attributes
        )
    }

    @Test
    fun `end an unknown fragment`() {
        assertTrue(dataSource.endFragment("my_fragment"))
        assertTrue(spanService.createdSpans.isEmpty())
    }
}
