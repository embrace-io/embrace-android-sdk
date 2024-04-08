package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.TapsLocalConfig
import io.embrace.android.embracesdk.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.injection.fakeDataSourceModule
import io.embrace.android.embracesdk.fakes.system.mockActivity
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.TapBreadcrumb
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceBreadcrumbServiceTest {

    private lateinit var spanService: FakeSpanService
    private lateinit var configService: ConfigService
    private lateinit var processStateService: ProcessStateService
    private lateinit var memoryCleanerService: EmbraceMemoryCleanerService
    private lateinit var activity: Activity
    private val clock = FakeClock()

    @Before
    fun createMocks() {
        spanService = FakeSpanService()
        configService = FakeConfigService(
            breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        taps = TapsLocalConfig(true),
                        webViewConfig = WebViewLocalConfig(true)
                    )
                },
                remoteCfg = {
                    RemoteConfig()
                }
            )
        )
        processStateService = FakeProcessStateService()
        activity = mockActivity()
        memoryCleanerService = EmbraceMemoryCleanerService(InternalEmbraceLogger())
        clock.setCurrentTime(MILLIS_FOR_2020_01_01)
        clock.tickSecond()
    }

    private fun assertJsonMessage(service: EmbraceBreadcrumbService, expected: String) {
        val message = SessionMessage(
            session = fakeSession(),
            breadcrumbs = service.getBreadcrumbs()
        )
        assertJsonMatchesGoldenFile(expected, message)
    }

    /*
     * Views
     */
    @Test
    fun testViewCreate() {
        val service = EmbraceBreadcrumbService(
            clock,
            configService,
            FakeActivityTracker(),
            { fakeDataSourceModule() },
            InternalEmbraceLogger(),
        )
        service.logView("viewA", clock.now())
        clock.tickSecond()
        service.logView("viewB", clock.now())
        clock.tickSecond()
        service.onViewClose(activity)
        assertJsonMessage(service, "breadcrumb_view.json")
    }

    // TO DO: refactor BreadCrumbService to avoid accessing internal implementation
    @Test
    fun testCleanCollections() {
        val service = initializeBreadcrumbService()
        service.logTap(Pair(0f, 0f), "MyView", 0, TapBreadcrumb.TapBreadcrumbType.TAP)
        service.logRnAction("MyAction", 0, 5, mapOf("key" to "value"), 100, "success")
        service.logPushNotification(
            "title",
            "body",
            "topic",
            "id",
            5,
            9,
            PushNotificationBreadcrumb.NotificationType.NOTIFICATION
        )
        service.logView("test", clock.now())
        service.logWebView("https://example.com/path1", clock.now())
        service.startView("a")
        service.endView("a")

        val breadcrumbs = service.getBreadcrumbs()
        assertEquals(1, breadcrumbs.rnActionBreadcrumbs?.size)
        assertEquals(1, breadcrumbs.pushNotifications?.size)
        assertEquals(1, breadcrumbs.viewBreadcrumbs?.size)

        service.cleanCollections()

        val breadcrumbsAfterClean = service.getBreadcrumbs()
        assertEquals(0, breadcrumbsAfterClean.rnActionBreadcrumbs?.size)
        assertEquals(0, breadcrumbsAfterClean.pushNotifications?.size)
        assertEquals(0, breadcrumbsAfterClean.viewBreadcrumbs?.size)
    }

    @Test
    fun testForceLogView() {
        val service = initializeBreadcrumbService()
        service.forceLogView("a", 0)
        val crumbs = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("a", breadcrumb.screen)
    }

    @Test
    fun testReplaceFirstSessionView() {
        val service = initializeBreadcrumbService()
        service.logView("a", 0)
        service.replaceFirstSessionView("b", 2)

        val crumbs = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("b", breadcrumb.screen)
    }

    @Test
    fun testLogRnAction() {
        val service = initializeBreadcrumbService()
        service.logRnAction("MyAction", 0, 5, mapOf("key" to "value"), 100, "success")

        val crumbs = checkNotNull(service.getBreadcrumbs().rnActionBreadcrumbs)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("MyAction", breadcrumb.name)
        assertEquals("success", breadcrumb.output)
        assertEquals(100, breadcrumb.bytesSent)
        assertEquals(mapOf("key" to "value"), breadcrumb.properties)
    }

    @Test
    fun testGetLastViewBreadcrumbScreenName() {
        val service = initializeBreadcrumbService()
        assertNull(service.getLastViewBreadcrumbScreenName())
        service.logView("test", 0)
        assertEquals("test", service.getLastViewBreadcrumbScreenName())
    }

    @Test
    fun testLogPushNotification() {
        val service = initializeBreadcrumbService()
        service.logPushNotification(
            "title",
            "body",
            "topic",
            "id",
            5,
            9,
            PushNotificationBreadcrumb.NotificationType.NOTIFICATION
        )

        val crumbs = checkNotNull(service.getBreadcrumbs().pushNotifications)
        val breadcrumb = checkNotNull(crumbs.single())
        assertNull(breadcrumb.title)
        assertNull(breadcrumb.body)
        assertNull(breadcrumb.from)
        assertEquals("id", breadcrumb.id)
        assertEquals(5, breadcrumb.priority)
    }

    @Test
    fun testOnViewEnabled() {
        val service = initializeBreadcrumbService()
        service.onView(mockActivity())

        val crumbs = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("android.app.Activity", breadcrumb.screen)
    }

    @Test
    fun testBreadcrumbLimitExceeded() {
        val service = initializeBreadcrumbService()
        repeat(110) { count ->
            service.logView("a$count", count.toLong())
        }

        val crumbs = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs)
        assertEquals(100, crumbs.size)
        assertEquals("a109", crumbs.first().screen)
        assertEquals("a10", crumbs.last().screen)
    }

    @Test
    fun `addFirstViewBreadcrumbForSession empty`() {
        val service = initializeBreadcrumbService()
        service.addFirstViewBreadcrumbForSession(0)
        val crumbs = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs)
        assertTrue(crumbs.isEmpty())
    }

    @Test
    fun `addFirstViewBreadcrumbForSession last screen`() {
        val service = initializeBreadcrumbService()
        service.logView("MyView", 0)
        service.addFirstViewBreadcrumbForSession(5)
        val crumb = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs).single()
        assertEquals("MyView", crumb.screen)
        assertEquals(5L, crumb.start)
    }

    @Test
    fun `addFirstViewBreadcrumbForSession activity tracker`() {
        val activityTracker = FakeActivityTracker().apply {
            foregroundActivity = mockActivity()
        }
        val service = EmbraceBreadcrumbService(
            clock,
            configService,
            activityTracker,
            { fakeDataSourceModule() },
            InternalEmbraceLogger(),
        )
        service.addFirstViewBreadcrumbForSession(5)
        val crumb = checkNotNull(service.getBreadcrumbs().viewBreadcrumbs).single()
        assertEquals("MyMockActivity", crumb.screen)
        assertEquals(5L, crumb.start)
    }

    private fun initializeBreadcrumbService() = EmbraceBreadcrumbService(
        clock,
        configService,
        FakeActivityTracker(),
        { fakeDataSourceModule() },
        InternalEmbraceLogger(),
    )

    companion object {
        private const val MILLIS_FOR_2020_01_01 = 1577836800000L
    }
}
