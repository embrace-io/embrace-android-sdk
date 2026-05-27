package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ScreenDataSourceTest {
    private lateinit var dataSource: ScreenDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(
            ApplicationProvider.getApplicationContext(),
            sessionIdSupplier = { "test-session" },
        )
        dataSource = ScreenInstrumentationProvider().register(args).dataSource as ScreenDataSource
    }

    @Test
    fun `data source not initialized by default`() {
        assertTrue(args.destination.createdStateTokens.isEmpty())
        assertEquals("Uninitialized", dataSource.getCurrentStateValue())
        assertFalse(dataSource.isActive())
    }

    @Test
    fun `onScreenLoaded updates the current state value and records each transition at the SDK clock time`() {
        val firstTime = args.clock.tick()
        dataSource.onScreenLoaded("home")
        assertEquals("home", dataSource.getCurrentStateValue())
        assertTrue(args.destination.createdStateTokens.isNotEmpty())
        assertTrue(dataSource.isActive())

        val secondTime = args.clock.tick()
        dataSource.onScreenLoaded("settings")
        assertEquals("settings", dataSource.getCurrentStateValue())

        val stateSpanToken = args.destination.createdStateTokens.single()
        assertEquals(listOf(firstTime to "home", secondTime to "settings"), stateSpanToken.transitions)
    }

    @Test
    fun `repeated onScreenLoaded with the same screen is deduplicated`() {
        dataSource.onScreenLoaded("home")
        args.clock.tick()
        dataSource.onScreenLoaded("home")
        assertEquals("home", dataSource.getCurrentStateValue())
    }
}
