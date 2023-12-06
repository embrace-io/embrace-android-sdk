package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.TapsLocalConfig
import io.embrace.android.embracesdk.config.local.WebViewLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.TapBreadcrumb
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch

internal class EmbraceBreadcrumbServiceTest {

    private lateinit var configService: ConfigService
    private lateinit var processStateService: ProcessStateService
    private lateinit var memoryCleanerService: EmbraceMemoryCleanerService
    private lateinit var activity: Activity
    private val logger = InternalEmbraceLogger()
    private val serializer = EmbraceSerializer()
    private val clock = FakeClock()

    @Before
    fun createMocks() {
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
        activity = mockk()
        memoryCleanerService = EmbraceMemoryCleanerService()
        clock.setCurrentTime(MILLIS_FOR_2020_01_01)
        clock.tickSecond()
    }

    private fun assertEmptyDataToStart(service: EmbraceBreadcrumbService) {
        assertTrue(
            "stack should be empty to start",
            service.fragmentStack.isEmpty(),
        )
        assertTrue(
            "no breadcrumbs to start",
            service.fragmentBreadcrumbs.isEmpty()
        )
    }

    private fun assertJsonMessage(service: EmbraceBreadcrumbService, expected: String) {
        val message = SessionMessage(
            session = fakeSession(),
            breadcrumbs = service.getBreadcrumbs(
                0, clock.now()
            )
        )
        val jsonMessage = serializer.toJson(message)
        assertEquals(jsonMessage, expected)
    }

    /*
     * Views
     */
    @Test
    fun testViewCreate() {
        val service = EmbraceBreadcrumbService(
            clock,
            configService,
            InternalEmbraceLogger()
        )
        service.logView("viewA", clock.now())
        clock.tickSecond()
        service.logView("viewB", clock.now())
        clock.tickSecond()
        service.onViewClose(activity)
        val expected = ResourceReader.readResourceAsText("breadcrumb_view.json")
            .filter { !it.isWhitespace() }
        assertJsonMessage(service, expected)
    }

    /*
     * Web views
     */
    @Test
    fun testWebViewCreate() {
        val service = initializeBreadcrumbService()
        clock.tickSecond()
        service.logWebView("https://example.com/path1", clock.now())
        clock.tickSecond()
        service.logWebView("https://example.com/path2", clock.now())
        val webViews = service.webViewBreadcrumbs
        assertEquals("two webviews captured", 2, webViews.size)
        val expected = ResourceReader.readResourceAsText("breadcrumb_webview.json")
            .filter { !it.isWhitespace() }
        assertJsonMessage(service, expected)
    }

    /*
     * Custom breadcrumbs
     */
    @Test
    fun testBreadcrumbCreate() {
        val service = initializeBreadcrumbService()
        service.logCustom("breadcrumb", clock.now())
        val breadcrumbs = service.customBreadcrumbs
        assertEquals("one breadcrumb captured", 1, breadcrumbs.size)
        val expected = ResourceReader.readResourceAsText("breadcrumb_custom.json")
            .filter { !it.isWhitespace() }
        assertJsonMessage(service, expected)
    }

    /*
     * Fragments
     */
    @Test
    fun testFragmentStart() {
        val service = initializeBreadcrumbService()
        assertTrue(service.fragmentStack.isEmpty())
        assertTrue(
            "starting view worked",
            service.startView("a")
        )
        assertEquals(
            "fragment stack has an entry",
            1,
            service.fragmentStack.size,
        )
        val fragment = service.fragmentStack[0]
        assertEquals(
            "right view name is captured",
            "a",
            fragment.name
        )
        assertTrue(
            "start time should be greater than 2020-01-01",
            fragment.getStartTime() > MILLIS_FOR_2020_01_01
        )
        assertEquals("end time is not set", 0L, fragment.endTime)
    }

    @Test
    fun testFragmentStartWithEnd() {
        val service = initializeBreadcrumbService()
        assertEmptyDataToStart(service)
        assertTrue(
            "starting fragment should succeed",
            service.startView("a"),
        )
        assertEquals(
            "should have one entry in the stack",
            1,
            service.fragmentStack.size
        )
        assertTrue(
            "ending fragment should succeed",
            service.endView("a")
        )
        assertTrue(
            "ending fragment should move it from the stack",
            service.fragmentStack.isEmpty()
        )
        assertEquals(
            "fragment should have been moved to the breadcrumb list",
            1,
            service.fragmentBreadcrumbs.size
        )
        val fragment = checkNotNull(service.fragmentBreadcrumbs.element())
        assertEquals("a", fragment.name)
        assertTrue(fragment.endTime > MILLIS_FOR_2020_01_01)
        assertTrue(fragment.getStartTime() > MILLIS_FOR_2020_01_01)
        assertTrue(fragment.getStartTime() <= fragment.endTime)
        assertFalse(
            "ending same fragment again should fail",
            service.endView("a")
        )
    }

    @Test
    fun testFragmentStartTooMany() {
        val service = initializeBreadcrumbService()
        assertEmptyDataToStart(service)
        val stackLimit = 20
        repeat(stackLimit) {
            assertTrue(
                "starting fragment should succeed",
                service.startView("a")
            )
        }
        assertFalse(
            "21st starting fragment should fail",
            service.startView("a")
        )
        assertEquals(
            "stack should have max values in it",
            stackLimit,
            service.fragmentStack.size
        )

        // can add more fragments once one is closed
        assertTrue(
            "closing a fragment should succeed",
            service.endView("a")
        )
        assertTrue(
            "should be able to start a fragment once we ended one",
            service.startView("a")
        )
        assertEquals(
            "we have closed one fragment",
            1,
            service.fragmentBreadcrumbs.size
        )
        assertEquals(
            "the stack is back full again",
            stackLimit,
            service.fragmentStack.size
        )
    }

    @Test
    fun testFragmentEndUnknown() {
        val service = initializeBreadcrumbService()
        assertEmptyDataToStart(service)
        assertTrue(
            "starting fragment should succeed",
            service.startView("a")
        )
        assertEquals(
            "one fragment should be on the stack",
            1,
            service.fragmentStack.size
        )
        assertFalse(
            "ending an unknown fragment should fail",
            service.endView("b")
        )
        assertEquals(
            "the opened fragment should be on the stack",
            1,
            service.fragmentStack.size
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFragmentAddFromMultipleThreads() {
        val service = initializeBreadcrumbService()
        assertEmptyDataToStart(service)
        val viewNames =
            "abcdefghij".split("".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val startSignal = CountDownLatch(1)
        val doneSignal = CountDownLatch(viewNames.size)
        for (viewName in viewNames) {
            // start workers that will all add a fragment each
            Thread(AddFragmentWorker(startSignal, doneSignal, service, viewName)).start()
        }
        startSignal.countDown()
        // wait for all the workers to finish
        doneSignal.await()
        assertTrue(
            "there should be no unclosed views",
            service.fragmentStack.isEmpty()
        )
        val actualViews = ArrayList<String>()
        for (fragmentBreadcrumb in service.fragmentBreadcrumbs) {
            actualViews.add(checkNotNull(fragmentBreadcrumb).name)
        }
        actualViews.sort()
        assertEquals(
            "the expected views were not found",
            actualViews,
            listOf(*viewNames)
        )
    }

    internal inner class AddFragmentWorker(
        private val startSignal: CountDownLatch,
        private val doneSignal: CountDownLatch,
        private val service: BreadcrumbService,
        private val viewName: String?
    ) : Runnable {
        override fun run() {
            try {
                startSignal.await()
                service.startView(viewName)
                Thread.sleep((Math.random() * 100).toLong())
                service.endView(viewName)
                doneSignal.countDown()
            } catch (ex: InterruptedException) {
                Assert.fail("worker thread died")
            }
        }
    }

    @Test
    fun testFragmentEndOnClose() {
        val service = initializeBreadcrumbService()
        assertEmptyDataToStart(service)
        assertTrue(
            "starting fragment should succeed",
            service.startView("a")
        )
        assertTrue(
            "starting fragment should succeed",
            service.startView("b")
        )
        assertEquals(
            "should have a stack with 2 entries",
            2,
            service.fragmentStack.size
        )
        service.onViewClose(activity)
        assertTrue(
            "should have an empty stack after activity close",
            service.fragmentStack.isEmpty()
        )
        assertEquals(
            "should have two fragment breadcrumbs",
            2,
            service.fragmentBreadcrumbs.size
        )
        service.onViewClose(activity)
        assertEquals(
            "should still have two fragment breadcrumbs",
            2,
            service.fragmentBreadcrumbs.size
        )
    }

    /*
     * All breadcrumbs
     */
    @Test
    fun testClean() {
        // TODO: add data to lists other than just fragments
        val service = initializeBreadcrumbService()
        assertEmptyDataToStart(service)
        assertTrue(
            "starting fragment should succeed",
            service.startView("a")
        )
        assertTrue(
            "ending fragment should succeed",
            service.endView("a")
        )
        assertEquals(
            "should have one fragment breadcrumb",
            1,
            service.fragmentBreadcrumbs.size
        )
        service.cleanCollections()
        assertTrue(
            "should not have any fragment breadcrumbs",
            service.fragmentBreadcrumbs.isEmpty()
        )
    }

    // TO DO: refactor BreadCrumbService to avoid accessing internal implementation
    @Test
    fun testCleanCollections() {
        val service = initializeBreadcrumbService()
        service.logTap(android.util.Pair(0f, 0f), "MyView", 0, TapBreadcrumb.TapBreadcrumbType.TAP)
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
        service.logCustom("a breadcrumb", clock.now())
        service.startView("a")
        service.endView("a")

        val breadcrumbs = service.getBreadcrumbs(0, clock.now())
        assertEquals(1, breadcrumbs.tapBreadcrumbs?.size)
        assertEquals(1, breadcrumbs.rnActionBreadcrumbs?.size)
        assertEquals(1, breadcrumbs.pushNotifications?.size)
        assertEquals(1, breadcrumbs.viewBreadcrumbs?.size)
        assertEquals(1, breadcrumbs.webViewBreadcrumbs?.size)
        assertEquals(1, breadcrumbs.customBreadcrumbs?.size)
        assertEquals(1, breadcrumbs.fragmentBreadcrumbs?.size)

        service.cleanCollections()

        val breadcrumbsAfterClean = service.getBreadcrumbs(0, clock.now())
        assertEquals(0, breadcrumbsAfterClean.tapBreadcrumbs?.size)
        assertEquals(0, breadcrumbsAfterClean.rnActionBreadcrumbs?.size)
        assertEquals(0, breadcrumbsAfterClean.pushNotifications?.size)
        assertEquals(0, breadcrumbsAfterClean.viewBreadcrumbs?.size)
        assertEquals(0, breadcrumbsAfterClean.webViewBreadcrumbs?.size)
        assertEquals(0, breadcrumbsAfterClean.customBreadcrumbs?.size)
        assertEquals(0, breadcrumbsAfterClean.fragmentBreadcrumbs?.size)
    }
    @Test
    fun testGetBreadcrumbs() {
        val service = initializeBreadcrumbService()
        assertTrue(
            "starting fragment should succeed",
            service.startView("a")
        )
        clock.tickSecond()
        assertTrue(
            "ending fragment should succeed",
            service.endView("a")
        )
        clock.tickSecond()
        assertTrue(
            "starting fragment should succeed",
            service.startView("b")
        )
        clock.tickSecond()
        service.onViewClose(activity)
        val message = SessionMessage(
            session = fakeSession(),
            breadcrumbs = service.getBreadcrumbs(
                0, clock.now()
            )
        )
        val jsonMessage = serializer.toJson(message)
        val expected = ResourceReader.readResourceAsText("breadcrumb_fragment.json")
            .filter { !it.isWhitespace() }
        assertEquals(expected, jsonMessage)
    }

    @Test
    fun testFlushBreadcrumbs() {
        val service = initializeBreadcrumbService()
        service.startView("a")
        clock.tickSecond()
        service.endView("a")
        clock.tickSecond()
        service.startView("b")
        clock.tickSecond()
        service.logCustom("breadcrumb", clock.now())
        val breadcrumbs = service.customBreadcrumbs
        assertEquals("one breadcrumb captured", 1, breadcrumbs.size)

        service.onViewClose(activity)
        val message = SessionMessage(
            session = fakeSession(),
            breadcrumbs = service.flushBreadcrumbs()
        )
        val jsonMessage = serializer.toJson(message)
        val expected = ResourceReader.readResourceAsText("breadcrumb_view_custom.json")
            .filter { !it.isWhitespace() }
        assertEquals(expected, jsonMessage)

        val secondMessage = SessionMessage(
            session = fakeSession(),
            breadcrumbs = service.flushBreadcrumbs()
        )
        val secondJsonMessage = serializer.toJson(secondMessage)
        val expectedEmpty = ResourceReader.readResourceAsText("breadcrumb_empty.json")
            .filter { !it.isWhitespace() }
        assertEquals(expectedEmpty, secondJsonMessage)
    }

    @Test
    fun testForceLogView() {
        val service = initializeBreadcrumbService()
        service.forceLogView("a", 0)
        val crumbs = service.getViewBreadcrumbsForSession(0, Long.MAX_VALUE)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("a", breadcrumb.screen)
    }

    @Test
    fun testReplaceFirstSessionView() {
        val service = initializeBreadcrumbService()
        service.logView("a", 0)
        service.replaceFirstSessionView("b", 2)

        val crumbs = service.getViewBreadcrumbsForSession(0, Long.MAX_VALUE)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("b", breadcrumb.screen)
    }

    @Test
    fun testLogTap() {
        val service = initializeBreadcrumbService()
        service.logTap(android.util.Pair(0f, 0f), "MyView", 0, TapBreadcrumb.TapBreadcrumbType.TAP)

        val crumbs = service.getTapBreadcrumbsForSession(0, Long.MAX_VALUE)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("MyView", breadcrumb.tappedElementName)
        assertEquals(TapBreadcrumb.TapBreadcrumbType.TAP, breadcrumb.type)
    }

    @Test
    fun testLogRnAction() {
        val service = initializeBreadcrumbService()
        service.logRnAction("MyAction", 0, 5, mapOf("key" to "value"), 100, "success")

        val crumbs = service.getRnActionBreadcrumbForSession(0, Long.MAX_VALUE)
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

        val crumbs = service.getPushNotificationsBreadcrumbsForSession(0, Long.MAX_VALUE)
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
        service.onView(mockk(relaxed = true))

        val crumbs = service.getViewBreadcrumbsForSession(0, Long.MAX_VALUE)
        val breadcrumb = checkNotNull(crumbs.single())
        assertEquals("android.app.Activity", breadcrumb.screen)
    }

    @Test
    fun testBreadcrumbLimitExceeded() {
        val service = initializeBreadcrumbService()
        repeat(110) { count ->
            service.logView("a$count", count.toLong())
        }

        val crumbs = service.getViewBreadcrumbsForSession(0, Long.MAX_VALUE)
        assertEquals(100, crumbs.size)
        assertEquals("a109", crumbs.first()?.screen)
        assertEquals("a10", crumbs.last()?.screen)
    }

    private fun initializeBreadcrumbService() = EmbraceBreadcrumbService(
        clock,
        configService,
        logger
    )

    companion object {
        private const val MILLIS_FOR_2020_01_01 = 1577836800000L
    }
}
