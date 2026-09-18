package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NavigationStateDataSourceTest {
    private lateinit var dataSource: NavigationStateDataSource
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        dataSource = NavigationStateDataSource(args).apply { onDataCaptureEnabled() }
    }

    @Test
    fun `state updated when the current screen changes`() {
        assertEquals(Screen("Initializing"), dataSource.getCurrentStateValue())
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen("home", args.clock.tick()))
        assertEquals(Screen("home"), dataSource.getCurrentStateValue())
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen("settings", args.clock.tick()))
        assertEquals(Screen("settings"), dataSource.getCurrentStateValue())
    }

    @Test
    fun `a data source enabled after a screen change is replayed the current screen`() {
        args.eventBus.emitState(CurrentScreen.KEY, CurrentScreen("home", args.clock.tick()))

        val lateDataSource = NavigationStateDataSource(args).apply { onDataCaptureEnabled() }

        assertEquals(Screen("home"), lateDataSource.getCurrentStateValue())
    }
}
