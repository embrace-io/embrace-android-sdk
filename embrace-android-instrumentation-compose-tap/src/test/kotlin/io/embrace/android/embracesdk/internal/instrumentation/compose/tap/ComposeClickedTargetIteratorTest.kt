package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.compose.tap.fakes.FakeScreenViewFactory
import io.embrace.android.embracesdk.internal.instrumentation.compose.tap.fakes.PositionedFrameLayout
import io.embrace.android.embracesdk.internal.instrumentation.compose.tap.fakes.PositionedView
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.TapDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ComposeClickedTargetIteratorTest {

    private lateinit var context: Context
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var iterator: ComposeClickedTargetIterator

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        context = application
        args = FakeInstrumentationArgs(application)
        val tapDataSource = TapDataSource(args)
        val composeTapDataSource = ComposeTapDataSource(args) { tapDataSource }
        iterator = ComposeClickedTargetIterator(args.logger, composeTapDataSource)
    }

    @Test
    fun `tap inside body is a no-op when there is no ComposeView`() {
        val decor = FakeScreenViewFactory.createScreen(context)

        iterator.findTarget(decor, x = 540f, y = 1200f)

        assertTrue(args.logger.errorMessages.isEmpty())
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    @Test
    fun `tap outside screen is a no-op`() {
        val decor = FakeScreenViewFactory.createScreen(context)

        iterator.findTarget(decor, x = 2000f, y = 3000f)

        assertTrue(args.logger.errorMessages.isEmpty())
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    @Test
    fun `tap at top-left corner of a child is treated as inside the child`() {
        val inner = PositionedView(context, winX = 100, winY = 200, width = 50, height = 50)
        val child = PositionedFrameLayout(context, winX = 100, winY = 200, width = 50, height = 50)
        child.addView(inner)
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        decor.addView(child)

        iterator.findTarget(decor, x = 100f, y = 200f)

        // child was queued and popped — so its children's location was queried during the bounds check
        assertEquals(1, inner.locationCheckCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `tap at the right edge of a child is treated as outside the child`() {
        val inner = PositionedView(context, winX = 100, winY = 200, width = 50, height = 50)
        val child = PositionedFrameLayout(context, winX = 100, winY = 200, width = 50, height = 50)
        child.addView(inner)
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        decor.addView(child)

        // x == child.winX + child.width: exclusive upper bound, should fall outside
        iterator.findTarget(decor, x = 150f, y = 200f)

        assertEquals(0, inner.locationCheckCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `tap on an invisible child is treated as outside`() {
        val inner = PositionedView(context, winX = 100, winY = 200, width = 50, height = 50)
        val child = PositionedFrameLayout(context, winX = 100, winY = 200, width = 50, height = 50)
        child.visibility = View.GONE
        child.addView(inner)
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        decor.addView(child)

        iterator.findTarget(decor, x = 100f, y = 200f)

        assertEquals(0, inner.locationCheckCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `clipChildren=false visits children that are outside parent bounds`() {
        val inner = PositionedView(context, winX = 2000, winY = 0, width = 10, height = 10)
        val outOfBoundsChild = PositionedFrameLayout(context, winX = 2000, winY = 0, width = 100, height = 100)
        outOfBoundsChild.addView(inner)
        val container = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        container.clipChildren = false
        container.addView(outOfBoundsChild)
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        decor.addView(container)

        iterator.findTarget(decor, x = 50f, y = 50f)

        // outOfBoundsChild was added to the queue without a bounds check and processed,
        // so its child's location was queried
        assertEquals(1, inner.locationCheckCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `clipChildren=true skips children that are outside parent bounds`() {
        val inner = PositionedView(context, winX = 2000, winY = 0, width = 10, height = 10)
        val outOfBoundsChild = PositionedFrameLayout(context, winX = 2000, winY = 0, width = 100, height = 100)
        outOfBoundsChild.addView(inner)
        val container = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        // clipChildren defaults to true
        container.addView(outOfBoundsChild)
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        decor.addView(container)

        iterator.findTarget(decor, x = 50f, y = 50f)

        assertEquals(0, inner.locationCheckCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `exception during traversal is caught and logged`() {
        val throwingChild = object : View(context) {
            init { layout(0, 0, 100, 100) }
            override fun getLocationInWindow(outLocation: IntArray) {
                error("boom")
            }
        }
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 1000, height = 1000)
        decor.addView(throwingChild)

        iterator.findTarget(decor, x = 50f, y = 50f)

        assertEquals(1, args.logger.errorMessages.size)
        assertEquals("Failed to find target", args.logger.errorMessages[0].msg)
        assertTrue(args.destination.addedEvents.isEmpty())
    }

    @Test
    fun `iterator handles an empty ComposeView in the hierarchy without crashing`() {
        // wrap the ComposeView in a positioned, clipChildren=false parent so it's queued and
        // popped regardless of its own (unmeasured) bounds — this exercises the iterator's
        // ViewGroup branch on a real ComposeView with no children
        val composeView = ComposeView(context)
        val wrapper = PositionedFrameLayout(context, winX = 0, winY = 0, width = 100, height = 100)
        wrapper.clipChildren = false
        wrapper.addView(composeView)
        val decor = PositionedFrameLayout(context, winX = 0, winY = 0, width = 100, height = 100)
        decor.addView(wrapper)

        iterator.findTarget(decor, x = 50f, y = 50f)

        assertTrue(args.logger.errorMessages.isEmpty())
        assertTrue(args.destination.addedEvents.isEmpty())
    }
}
