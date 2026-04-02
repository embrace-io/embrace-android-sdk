package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
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
        dataSource = NavigationStateDataSource(args, true)
    }

    @Test
    fun `state updated when notified of new screen load`() {
        assertEquals(Screen("Initializing"), dataSource.getCurrentStateValue())
        dataSource.onScreenLoad(args.clock.tick(), "home")
        assertEquals(Screen("home"), dataSource.getCurrentStateValue())
        dataSource.onScreenLoad(args.clock.tick(), "settings")
        assertEquals(Screen("settings"), dataSource.getCurrentStateValue())
    }
}
