package io.embrace.android.embracesdk.capture.crumbs

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.spans.EmbraceAttributes
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
            configService,
            clock,
            spanService,
        )
    }

    @Test
    fun `fragment with start`() {
        dataSource.startFragment("my_fragment")

        val span = spanService.createdSpans.single()
        assertEquals("screen-view", span.name)
        assertEquals(EmbraceAttributes.Type.PERFORMANCE, span.type)
        assertTrue(span.isRecording)

        assertEquals("my_fragment", span.attributes["fragment_name"])
        assertEquals(clock.now().toString(), span.attributes["start_time"])
    }

    @Test
    fun `fragment with start and end`() {
        val start = clock.now()
        dataSource.startFragment("my_fragment")
        clock.tick(30000)
        dataSource.endFragment("my_fragment")

        val span = spanService.createdSpans.single()
        assertEquals("screen-view", span.name)
        assertEquals(EmbraceAttributes.Type.PERFORMANCE, span.type)
        assertFalse(span.isRecording)
        assertEquals("my_fragment", span.attributes["fragment_name"])
        assertEquals(start.toString(), span.attributes["start_time"])
        assertEquals(clock.now().toString(), span.attributes["end_time"])
    }

    @Test
    fun `end an unknown fragment`() {
        dataSource.endFragment("my_fragment")
        assertTrue(spanService.createdSpans.isEmpty())
    }
}
