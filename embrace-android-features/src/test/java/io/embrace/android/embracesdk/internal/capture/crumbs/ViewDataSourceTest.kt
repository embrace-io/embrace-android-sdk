package io.embrace.android.embracesdk.internal.capture.crumbs

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeTraceWriter
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ViewDataSourceTest {

    private lateinit var configService: FakeConfigService
    private lateinit var clock: FakeClock
    private lateinit var traceWriter: FakeTraceWriter
    private lateinit var dataSource: ViewDataSource

    @Before
    fun setUp() {
        configService = FakeConfigService()
        clock = FakeClock()
        traceWriter = FakeTraceWriter()
        dataSource = ViewDataSource(
            configService.breadcrumbBehavior,
            clock,
            traceWriter,
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `start view creates a span correctly`() {
        dataSource.startView("my_fragment")

        val span = traceWriter.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertTrue(span.isRecording)
        assertEquals(
            mapOf(
                "view.name" to "my_fragment",
                EmbType.Ux.View.asPair(),
            ),
            span.attributes
        )
    }

    @Test
    fun `start view ends span with the same name`() {
        dataSource.startView("my_fragment")
        dataSource.startView("my_fragment")

        val spans = traceWriter.createdSpans

        assertEquals(2, spans.size)
        assertTrue(
            spans.all {
                it.type == EmbType.Ux.View &&
                    it.attributes["view.name"] == "my_fragment" &&
                    it.attributes["emb.type"] == "ux.view"
            }
        )

        val firstSpan = spans.first()
        val secondSpan = spans.last()

        assertFalse(firstSpan.isRecording)
        assertTrue(secondSpan.isRecording)
    }

    @Test
    fun `start view doesn't end span with different name`() {
        dataSource.startView("my_fragment")
        dataSource.startView("another_fragment")

        val spans = traceWriter.createdSpans

        assertEquals(2, spans.size)
        assertTrue(
            spans.all {
                it.type == EmbType.Ux.View &&
                    it.attributes["emb.type"] == "ux.view" &&
                    it.isRecording
            }
        )

        val firstSpan = spans.first()
        val secondSpan = spans.last()

        assertEquals("my_fragment", firstSpan.attributes["view.name"])
        assertEquals("another_fragment", secondSpan.attributes["view.name"])
    }

    @Test
    fun `end view stops existing span`() {
        dataSource.startView("my_fragment")
        dataSource.endView("my_fragment")

        val span = traceWriter.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertFalse(span.isRecording)
        assertEquals(
            mapOf(
                "view.name" to "my_fragment",
                EmbType.Ux.View.asPair()
            ),
            span.attributes
        )
    }

    @Test
    fun `end an unknown fragment`() {
        assertTrue(dataSource.endView("my_fragment"))
        assertTrue(traceWriter.createdSpans.isEmpty())
    }

    @Test
    fun `change view starts a new span`() {
        dataSource.changeView("some_view")

        val span = traceWriter.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertTrue(span.isRecording)
        assertEquals(
            mapOf(
                "view.name" to "some_view",
                EmbType.Ux.View.asPair(),
            ),
            span.attributes
        )
    }

    @Test
    fun `change view ends last span added`() {
        dataSource.changeView("a_view")
        dataSource.changeView("another_view")

        val spans = traceWriter.createdSpans

        assertEquals(2, spans.size)
        assertTrue(
            spans.all {
                it.type == EmbType.Ux.View &&
                    it.attributes["emb.type"] == "ux.view"
            }
        )

        val firstSpan = spans.first()
        val secondSpan = spans.last()

        assertFalse(firstSpan.isRecording)
        assertTrue(secondSpan.isRecording)
        assertTrue(firstSpan.attributes["view.name"] == "a_view")
        assertTrue(secondSpan.attributes["view.name"] == "another_view")
    }
}
