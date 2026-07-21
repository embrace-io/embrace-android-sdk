package io.embrace.android.embracesdk.internal.instrumentation.view

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbViewAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
internal class ViewDataSourceTest {

    private lateinit var dataSource: ViewDataSource
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        dataSource = ViewDataSource(args)
        activity = Activity()
    }

    @Test
    fun `start view creates a span correctly`() {
        dataSource.startView("my_fragment")

        val span = args.destination.createdSpans.single()
        assertEquals(EmbType.Ux.View, span.type)
        assertTrue(span.isRecording())
        assertEquals(
            mapOf(
                EmbViewAttributes.VIEW_NAME to "my_fragment",
                EmbType.Ux.View.asPair(),
            ),
            span.attributes,
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
                    it.attributes[EmbViewAttributes.VIEW_NAME] == "my_fragment" &&
                    it.attributes["emb.type"] == "ux.view"
            },
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
            },
        )

        val firstSpan = spans.first()
        val secondSpan = spans.last()

        assertEquals("my_fragment", firstSpan.attributes[EmbViewAttributes.VIEW_NAME])
        assertEquals("another_fragment", secondSpan.attributes[EmbViewAttributes.VIEW_NAME])
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
                EmbViewAttributes.VIEW_NAME to "my_fragment",
                EmbType.Ux.View.asPair(),
            ),
            span.attributes,
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
                EmbViewAttributes.VIEW_NAME to "some_view",
                EmbType.Ux.View.asPair(),
            ),
            span.attributes,
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
            },
        )

        val firstSpan = spans.first()
        val secondSpan = spans.last()

        assertFalse(firstSpan.isRecording())
        assertTrue(secondSpan.isRecording())
        assertTrue(firstSpan.attributes[EmbViewAttributes.VIEW_NAME] == "a_view")
        assertTrue(secondSpan.attributes[EmbViewAttributes.VIEW_NAME] == "another_view")
    }

    @Test
    fun `onViewClose stops all spans and clears tracking`() {
        dataSource.startView("a")
        dataSource.startView("b")

        dataSource.onViewClose()

        val spans = args.destination.createdSpans
        assertEquals(2, spans.size)
        assertTrue(spans.none { it.isRecording() })
        assertEquals(0, dataSource.trackedViewCount)
    }

    @Test
    fun `repeated dynamic startView then onViewClose does not retain entries`() {
        repeat(100) { dataSource.startView("view_$it") }
        assertEquals(100, dataSource.trackedViewCount)

        dataSource.onViewClose()

        assertEquals(0, dataSource.trackedViewCount)
    }

    @Test
    fun `changeView after onViewClose does not resurrect a stale view`() {
        dataSource.startView("stale")
        dataSource.onViewClose()

        dataSource.changeView("fresh")

        val spans = args.destination.createdSpans
        assertEquals(2, spans.size)
        val freshSpan = spans.single { it.attributes[EmbViewAttributes.VIEW_NAME] == "fresh" }
        assertTrue(freshSpan.isRecording())
        assertEquals(1, dataSource.trackedViewCount)
    }

    @Test
    fun `concurrent startView and endView does not throw ConcurrentModificationException`() {
        val iterations = 100
        val errors = AtomicInteger(0)
        val latch = CountDownLatch(2)

        val starter = Thread {
            repeat(iterations) { i ->
                try {
                    dataSource.startView("view_$i")
                } catch (e: ConcurrentModificationException) {
                    errors.incrementAndGet()
                }
            }
            latch.countDown()
        }

        val ender = Thread {
            repeat(iterations) { i ->
                try {
                    dataSource.endView("view_$i")
                } catch (e: ConcurrentModificationException) {
                    errors.incrementAndGet()
                }
            }
            latch.countDown()
        }

        starter.start()
        ender.start()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals("ConcurrentModificationException detected", 0, errors.get())
    }

    @Test
    fun `starting view with isManual parameter creates a span marked as manual`() {
        dataSource.startView("my_fragment", true)
        val span = args.destination.createdSpans.single()
        verifyManualViewSpan(span, true)
    }

    @Test
    fun `activity start creates a view span without manual instrumentation indicator`() {
        dataSource.onActivityStarted(activity)
        val span = args.destination.createdSpans.single()
        verifyAutoCapturedViewSpan(span, true)
    }

    @Test
    fun `manual start view replacing an auto-captured view results in proper manual attribution`() {
        dataSource.onActivityStarted(activity)
        dataSource.startView(activity.javaClass.name, true)

        val spans = args.destination.createdSpans.filter { it.type == EmbType.Ux.View }
        assertEquals(2, spans.size)

        val firstSpan = spans.first()
        val secondSpan = spans.last()
        verifyAutoCapturedViewSpan(firstSpan, false)
        verifyManualViewSpan(secondSpan, true)
    }

    @Test
    fun `auto captured view replacing a manually started view results in proper manual attribution`() {
        dataSource.startView(activity.javaClass.name, true)
        dataSource.onActivityStarted(activity)

        val spans = args.destination.createdSpans.filter { it.type == EmbType.Ux.View }
        assertEquals(2, spans.size)

        val firstSpan = spans.first()
        val secondSpan = spans.last()
        verifyManualViewSpan(firstSpan, false)
        verifyAutoCapturedViewSpan(secondSpan, true)
    }

    @Test
    fun `manual ending of an auto started view is not marked as manual`() {
        dataSource.onActivityStarted(activity)
        dataSource.endView(activity.javaClass.name)

        val span = args.destination.createdSpans.single { it.type == EmbType.Ux.View }
        verifyAutoCapturedViewSpan(span, false)
    }

    @Test
    fun `auto ending of a manually started view is marked as manual`() {
        dataSource.startView(activity.javaClass.name, true)
        dataSource.onActivityStopped(activity)

        val span = args.destination.createdSpans.single { it.type == EmbType.Ux.View }
        verifyManualViewSpan(span, false)
    }

    private fun verifyManualViewSpan(span: FakeSpanToken, isRecording: Boolean) {
        assertEquals(EmbType.Ux.View, span.type)
        assertEquals(isRecording, span.isRecording())
        assertEquals("true", span.attributes[EmbCommonAttributes.EMB_MANUAL_INSTRUMENTATION])
    }

    private fun verifyAutoCapturedViewSpan(span: FakeSpanToken, isRecording: Boolean) {
        assertEquals(EmbType.Ux.View, span.type)
        assertEquals(isRecording, span.isRecording())
        assertNull(span.attributes[EmbCommonAttributes.EMB_MANUAL_INSTRUMENTATION])
    }
}
