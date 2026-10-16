package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
internal class AndroidxNavigationInstrumentationProviderTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var signals: MutableList<NavigationSignal>
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs()
        signals = CopyOnWriteArrayList()
        args.eventBus.addHandler(NavigationSignal.KEY) { signals.add(it) }
        activity = buildActivity(Activity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        createDataSource().apply {
            onDataCaptureEnabled()
            onDataCaptureDisabled()
        }
    }

    @Test
    fun `hooks emit signals while capture is enabled`() {
        val source = createDataSource()
        source.onDataCaptureEnabled()
        assertTrue(args.navigationTrackingService.navigationTrackingInitListener is NavControllerTracker)

        attachBackStack(activity)
        onBackStackDestinationChange(activity, "home")
        assertEquals(2, signals.size)
    }

    @Test
    fun `hooks are inert after capture is disabled`() {
        val source = createDataSource()
        source.onDataCaptureEnabled()
        source.onDataCaptureDisabled()

        trackNavigation(activity, null)
        attachBackStack(activity)
        onBackStackDestinationChange(activity, "home")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `disabling a stale data source does not reset hooks of a newer one`() {
        val factory = AndroidxNavigationInstrumentationProvider().register(args)
        val stale = checkNotNull(factory())
        val current = checkNotNull(factory())
        stale.onDataCaptureEnabled()
        current.onDataCaptureEnabled()
        stale.onDataCaptureDisabled()

        attachBackStack(activity)
        onBackStackDestinationChange(activity, "home")
        assertEquals(2, signals.size)
    }

    private fun createDataSource() = checkNotNull(AndroidxNavigationInstrumentationProvider().register(args)())
}
