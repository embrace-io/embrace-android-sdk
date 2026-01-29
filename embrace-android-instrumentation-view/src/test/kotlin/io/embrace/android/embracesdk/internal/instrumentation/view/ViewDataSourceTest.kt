package io.embrace.android.embracesdk.internal.instrumentation.view

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ViewDataSourceTest : RobolectricTest() {

    private lateinit var dataSource: ViewDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        dataSource = ViewDataSource(args)
    }

    @Test
    fun `start view creates a span correctly`() {
        dataSource.startView("my_fragment")

        val span = args.destination.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertTrue(span.isRecording())
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

        val spans = args.destination.createdSpans

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

        assertFalse(firstSpan.isRecording())
        assertTrue(secondSpan.isRecording())
    }

    @Test
    fun `start view doesn't end span with different name`() {
        dataSource.startView("my_fragment")
        dataSource.startView("another_fragment")

        val spans = args.destination.createdSpans

        assertEquals(2, spans.size)
        assertTrue(
            spans.all {
                it.type == EmbType.Ux.View &&
                    it.attributes["emb.type"] == "ux.view" &&
                    it.isRecording()
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

        val span = args.destination.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertFalse(span.isRecording())
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
        assertTrue(args.destination.createdSpans.isEmpty())
    }

    @Test
    fun `change view starts a new span`() {
        dataSource.changeView("some_view")

        val span = args.destination.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertTrue(span.isRecording())
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

        val spans = args.destination.createdSpans

        assertEquals(2, spans.size)
        assertTrue(
            spans.all {
                it.type == EmbType.Ux.View &&
                    it.attributes["emb.type"] == "ux.view"
            }
        )

        val firstSpan = spans.first()
        val secondSpan = spans.last()

        assertFalse(firstSpan.isRecording())
        assertTrue(secondSpan.isRecording())
        assertTrue(firstSpan.attributes["view.name"] == "a_view")
        assertTrue(secondSpan.attributes["view.name"] == "another_view")
    }
}
