package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
internal class NavigationStateDataSourceTest {
    private lateinit var dataSource: NavigationStateDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext(),
            sessionPartIdSupplier = { "session-part-id" },
        )
        dataSource = NavigationStateDataSource(args).apply { onDataCaptureEnabled() }
    }

    @Test
    fun `state updated when the current screen changes`() {
        assertEquals(Screen.Initializing, dataSource.getCurrentStateValue())
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("home"), args.clock.tick()))
        assertEquals(Screen.Named("home"), dataSource.getCurrentStateValue())
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("settings"), args.clock.tick()))
        assertEquals(Screen.Named("settings"), dataSource.getCurrentStateValue())
    }

    @Test
    fun `app screen with the name of system screen value is a distinct state value`() {
        // Make sure two screens with the same string representations are treated as distinct because one is a system state value
        val dupeNamedScreen = Screen.Named("Backgrounded")
        assertEquals(Screen.Backgrounded.toString(), dupeNamedScreen.toString())

        val token = args.destination.createdStateTokens.single()
        val appScreenTime = args.clock.tick()
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(dupeNamedScreen, appScreenTime))
        assertNotEquals(Screen.Backgrounded, dataSource.getCurrentStateValue())

        val backgroundTime = args.clock.tick()
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Backgrounded, backgroundTime))
        assertEquals(Screen.Backgrounded, dataSource.getCurrentStateValue())

        val foregroundTime = args.clock.tick()
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(dupeNamedScreen, foregroundTime))
        assertNotEquals(Screen.Backgrounded, dataSource.getCurrentStateValue())

        assertEquals(
            listOf(
                Pair(appScreenTime, dupeNamedScreen),
                Pair(backgroundTime, Screen.Backgrounded),
                Pair(foregroundTime, dupeNamedScreen),
            ),
            token.transitions,
        )
    }

    @Test
    fun `a data source enabled after a screen change is replayed the current screen`() {
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("home"), args.clock.tick()))

        val lateDataSource = NavigationStateDataSource(args).apply { onDataCaptureEnabled() }

        assertEquals(Screen.Named("home"), lateDataSource.getCurrentStateValue())
    }

    @Test
    fun `screen attributes for the screen being left are recorded on the transition away from it`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)
        val token = args.destination.createdStateTokens.single()

        showScreen(Screen.Named("home"))
        showScreen(Screen.Named("settings"))
        showScreen(Screen.Named("profile"))

        // leaving the Initializing system state records nothing
        assertEquals(
            listOf(emptyMap(), mapOf("screen.visit" to "2"), mapOf("screen.visit" to "3")),
            token.transitionAttributes,
        )
    }

    @Test
    fun `screen attributes from every source are merged`() {
        args.navigationTrackingService.addScreenAttributesSource { sink -> sink("a", "1") }
        args.navigationTrackingService.addScreenAttributesSource { sink -> sink("b", "2") }
        val token = args.destination.createdStateTokens.single()

        showScreen(Screen.Named("home"))
        showScreen(Screen.Named("settings"))

        assertEquals(mapOf("a" to "1", "b" to "2"), token.transitionAttributes.last())
    }

    @Test
    fun `leaving a system state records nothing but still collects, so its frames are not attributed to the next screen`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)
        val token = args.destination.createdStateTokens.single()

        showScreen(Screen.Named("home"))
        showScreen(Screen.Backgrounded)
        showScreen(Screen.Named("home"))

        assertEquals(
            listOf(emptyMap(), mapOf("screen.visit" to "2"), emptyMap()),
            token.transitionAttributes,
        )
        assertEquals(3, source.collectCount)
    }

    @Test
    fun `screen attributes are only collected when the screen actually changes`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)

        showScreen(Screen.Named("home"))
        showScreen(Screen.Named("home"))

        assertEquals(1, source.collectCount)
    }

    @Test
    fun `ending the state span does not collect screen attributes, so they carry over to the screen's departure`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)

        showScreen(Screen.Named("home"))
        dataSource.onPreSessionEnd()
        dataSource.onPostSessionChange()
        assertEquals(1, source.collectCount)

        showScreen(Screen.Named("settings"))
        val nextPartToken = args.destination.createdStateTokens.last()
        assertEquals(listOf(mapOf("screen.visit" to "2")), nextPartToken.transitionAttributes)
    }

    @Test
    fun `no screen attributes are recorded without a source`() {
        val token = args.destination.createdStateTokens.single()

        showScreen(Screen.Named("home"))
        dataSource.onPreSessionEnd()

        assertEquals(listOf(emptyMap<String, String>()), token.transitionAttributes)
    }

    private fun showScreen(screen: Screen) {
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(screen, args.clock.tick()))
    }

    @Test
    fun `transitions capped at limit`() {
        dataSource.onDataCaptureEnabled()
        val token = args.destination.createdStateTokens.single()

        repeat(1001) { i ->
            showScreen(Screen.Named(if (i % 2 == 0) "about" else "contacts"))
        }

        assertEquals(1000, token.transitions.size)
    }

    @Test
    fun `activity lifecycle callbacks are unregistered when data capture is disabled`() {
        val signals = mutableListOf<NavigationSignal>()
        args.eventBus.addHandler(NavigationSignal.KEY) { signals.add(it) }
        val tracker = ActivityNavigationTracker(args.clock, args.eventBus, args.navigationTrackingService)
        args.application.registerActivityLifecycleCallbacks(tracker)
        val source = NavigationStateDataSource(args, tracker).apply { onDataCaptureEnabled() }

        source.onDataCaptureDisabled()
        Robolectric.buildActivity(Activity::class.java).setup()
        assertTrue(signals.isEmpty())
    }

    private class CountingScreenAttributesSource : ScreenAttributesSource {
        var collectCount = 0

        override fun writeAttributes(setAttribute: (key: String, value: String) -> Unit) {
            collectCount++
            setAttribute("screen.visit", collectCount.toString())
        }
    }
}
