package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource
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

    @Test
    fun `screen attributes for the screen being left are recorded on the transition away from it`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)
        dataSource.onDataCaptureEnabled()
        val token = args.destination.createdStateTokens.single()

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("settings"))
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("profile"))

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
        dataSource.onDataCaptureEnabled()
        val token = args.destination.createdStateTokens.single()

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("settings"))

        assertEquals(mapOf("a" to "1", "b" to "2"), token.transitionAttributes.last())
    }

    @Test
    fun `leaving a system state records nothing but still collects, so its frames are not attributed to the next screen`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)
        dataSource.onDataCaptureEnabled()
        val token = args.destination.createdStateTokens.single()

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        dataSource.onScreenLoad(args.clock.tick(), Screen.Backgrounded)
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))

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
        dataSource.onDataCaptureEnabled()

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))

        assertEquals(1, source.collectCount)
    }

    @Test
    fun `ending the state span does not collect screen attributes, so they carry over to the screen's departure`() {
        val source = CountingScreenAttributesSource()
        args.navigationTrackingService.addScreenAttributesSource(source)
        dataSource.onDataCaptureEnabled()

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        dataSource.onPreSessionEnd()
        dataSource.onPostSessionChange()
        assertEquals(1, source.collectCount)

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("settings"))
        val nextPartToken = args.destination.createdStateTokens.last()
        assertEquals(listOf(mapOf("screen.visit" to "2")), nextPartToken.transitionAttributes)
    }

    @Test
    fun `no screen attributes are recorded without a source`() {
        dataSource.onDataCaptureEnabled()
        val token = args.destination.createdStateTokens.single()

        dataSource.onScreenLoad(args.clock.tick(), Screen.Named("home"))
        dataSource.onPreSessionEnd()

        assertEquals(listOf(emptyMap<String, String>()), token.transitionAttributes)
    }

    private class CountingScreenAttributesSource : ScreenAttributesSource {
        var collectCount = 0

        override fun writeAttributes(setAttribute: (key: String, value: String) -> Unit) {
            collectCount++
            setAttribute("screen.visit", collectCount.toString())
        }
    }
}
