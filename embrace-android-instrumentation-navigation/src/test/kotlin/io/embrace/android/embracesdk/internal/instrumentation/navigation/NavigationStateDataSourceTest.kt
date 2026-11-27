package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.appStateEnabledConfigService
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.state.StateDeclaration
import io.embrace.android.embracesdk.internal.state.StateEvents
import io.embrace.android.embracesdk.internal.state.StateReport
import io.embrace.android.embracesdk.internal.state.StateUpdate
import io.embrace.android.embracesdk.semconv.EmbFrameCountsAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalSemconv::class)
internal class NavigationStateDataSourceTest {
    private lateinit var dataSource: NavigationStateDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(
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
    fun `activity events resolve the current screen until data capture is disabled`() {
        val source = NavigationStateInstrumentationProvider().factoryProvider(args)().apply { onDataCaptureEnabled() }
        val resumed = DopeActivity()
        args.eventBus.startActivity(resumed)
        args.eventBus.resumeActivity(resumed)
        assertEquals(Screen.Named("DopeActivity"), args.eventBus.currentState(CurrentScreen.KEY)?.screen)

        val signals = mutableListOf<NavigationSignal>()
        args.eventBus.addHandler(NavigationSignal.KEY) { signals.add(it) }
        source.onDataCaptureDisabled()
        val activity = Activity()
        args.eventBus.startActivity(activity)
        args.eventBus.resumeActivity(activity)
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `with the app state model on the screen state declares the two frame count keys`() {
        val modelArgs = FakeInstrumentationArgs(configService = appStateEnabledConfigService())
        val declarations = mutableListOf<StateDeclaration>()
        modelArgs.eventBus.addHandler(StateEvents.DECLARED) { declarations.add(it) }

        NavigationStateDataSource(modelArgs).onDataCaptureEnabled()

        val declaration = declarations.single()
        assertEquals("screen-automatic", declaration.definition.name)
        assertEquals(1000, declaration.definition.changeLimit)
        assertEquals(
            setOf(EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES, EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES),
            declaration.definition.attributeKeys,
        )
        assertEquals("${Screen.Initializing}", declaration.initialValue)
        assertEquals(true, declaration.system)
    }

    @Test
    fun `with the app state model on a screen change is reported over the bus with the screen being left`() {
        val modelArgs = FakeInstrumentationArgs(configService = appStateEnabledConfigService())
        val updates = mutableListOf<StateUpdate>()
        modelArgs.eventBus.addHandler(StateEvents.UPDATED) { updates.add(it) }
        modelArgs.navigationTrackingService.addScreenAttributesSource(CountingScreenAttributesSource())
        NavigationStateDataSource(modelArgs).onDataCaptureEnabled()

        val homeTime = modelArgs.clock.tick()
        modelArgs.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("home"), homeTime))
        val settingsTime = modelArgs.clock.tick()
        modelArgs.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("settings"), settingsTime))

        assertEquals(listOf("screen-automatic", "screen-automatic"), updates.map { it.state })
        assertEquals(listOf("${Screen.Named("home")}", "${Screen.Named("settings")}"), updates.map { it.value })
        assertEquals(listOf(false, false), updates.map { it.system })
        assertEquals(listOf(homeTime, settingsTime), updates.map { it.timeMs })
        // nothing was showing before the first screen, whose collection is discarded, and the second change leaves the home screen
        assertEquals(emptyMap<String, String>(), updates[0].attributes)
        assertEquals(mapOf("screen.visit" to "2"), updates[1].attributes)
        assertEquals(emptyList<Any>(), modelArgs.destination.createdStateTokens)
    }

    @Test
    fun `with the app state model on an outside report of a screen carries the counts of the screen being left`() {
        val modelArgs = FakeInstrumentationArgs(configService = appStateEnabledConfigService())
        val updates = mutableListOf<StateUpdate>()
        modelArgs.eventBus.addHandler(StateEvents.UPDATED) { updates.add(it) }
        val source = CountingScreenAttributesSource()
        modelArgs.navigationTrackingService.addScreenAttributesSource(source)
        NavigationStateDataSource(modelArgs).onDataCaptureEnabled()

        modelArgs.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("home"), modelArgs.clock.tick()))
        val reportedTime = modelArgs.clock.tick()
        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("screen-automatic", "${Screen.Named("hosted")}", reportedTime))
        modelArgs.eventBus.emitState(CurrentScreen.KEY, CurrentScreen(Screen.Named("profile"), modelArgs.clock.tick()))

        assertEquals(
            listOf("${Screen.Named("home")}", "${Screen.Named("hosted")}", "${Screen.Named("profile")}"),
            updates.map { it.value },
        )
        assertEquals(reportedTime, updates[1].timeMs)
        assertEquals(false, updates[1].system)
        // leaving Initializing collects and discards; each screen left after that is collected once, from its own start
        assertEquals(
            listOf(emptyMap(), mapOf("screen.visit" to "2"), mapOf("screen.visit" to "3")),
            updates.map { it.attributes },
        )
        assertEquals(3, source.collectCount)
    }

    private class CountingScreenAttributesSource : ScreenAttributesSource {
        var collectCount = 0

        override fun writeAttributes(setAttribute: (key: String, value: String) -> Unit) {
            collectCount++
            setAttribute("screen.visit", collectCount.toString())
        }
    }
}
