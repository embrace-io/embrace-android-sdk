package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
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
        dataSource = NavigationStateDataSource(args)
    }

    @Test
    fun `state updated when notified of new screen load`() {
        assertEquals(Screen.Initializing, dataSource.getCurrentStateValue())
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        assertEquals(Screen.Named("home"), dataSource.getCurrentStateValue())
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("settings"))
        assertEquals(Screen.Named("settings"), dataSource.getCurrentStateValue())
    }

    @Test
    fun `app screen with the name of system screen value is a distinct state value`() {
        // Make sure two screens with the same string representations are treated as distinct because one is a system state value
        val dupeNamedScreen = Screen.Named("Backgrounded")
        assertEquals(Screen.Backgrounded.toString(), dupeNamedScreen.toString())

        dataSource.onDataCaptureEnabled()
        val token = args.destination.createdStateTokens.single()
        val appScreenTime = args.clock.tick()
        dataSource.onScreenLoad(appScreenTime, dupeNamedScreen)
        assertNotEquals(Screen.Backgrounded, dataSource.getCurrentStateValue())

        val backgroundTime = args.clock.tick()
        dataSource.onScreenLoad(backgroundTime, Screen.Backgrounded)
        assertEquals(Screen.Backgrounded, dataSource.getCurrentStateValue())

        val foregroundTime = args.clock.tick()
        dataSource.onScreenLoad(foregroundTime, dupeNamedScreen)
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
}
