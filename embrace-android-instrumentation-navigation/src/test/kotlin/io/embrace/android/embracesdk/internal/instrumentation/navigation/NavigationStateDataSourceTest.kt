package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}
