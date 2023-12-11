package io.embrace.android.embracesdk

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsQueue
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.SessionSnapshotType.JVM_CRASH
import io.embrace.android.embracesdk.session.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.session.SessionSnapshotType.PERIODIC_CACHE
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.nio.charset.Charset

internal class EmbraceDeliveryCacheManagerTest {

    private val prefix = "last_session"
    private val serializer = EmbraceSerializer()
    private val executor = MoreExecutors.newDirectExecutorService()
    private lateinit var deliveryCacheManager: EmbraceDeliveryCacheManager
    private lateinit var cacheService: CacheService
    private lateinit var memoryCleanerService: MemoryCleanerService
    private lateinit var fakeClock: FakeClock

    companion object {
        private const val clockInit = 1663800000000
        private lateinit var logger: InternalEmbraceLogger

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            logger = InternalEmbraceLogger()
        }
    }

    @Before
    fun before() {
        cacheService = spyk(TestCacheService())
        memoryCleanerService = mockk(relaxed = true)
        fakeClock = FakeClock(clockInit)
        initializeSessionCacheManager()
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    private fun initializeSessionCacheManager() {
        deliveryCacheManager = EmbraceDeliveryCacheManager(
            cacheService,
            executor,
            logger,
            fakeClock,
            serializer
        )
    }

    @Test
    fun `cache current session successfully`() {
        val sessionMessage = createSessionMessage("test_cache")
        val expectedBytes = serializer.toJson(sessionMessage).toByteArray()

        val serialized = deliveryCacheManager.saveSession(sessionMessage, NORMAL_END)

        assertArrayEquals(expectedBytes, serialized)

        verify {
            cacheService.cacheBytes(
                "$prefix.$clockInit.test_cache.json",
                expectedBytes
            )
        }

        assertSessionsMatch(sessionMessage, checkNotNull(deliveryCacheManager.loadSession("test_cache")))

        val expectedByteArray = serializer.toJson(sessionMessage).toByteArray()
        assertArrayEquals(expectedByteArray, checkNotNull(deliveryCacheManager.loadSessionBytes("test_cache")))
    }

    @Test
    fun `cache periodic session successful`() {
        val sessionMessage = createSessionMessage("test_cache")

        deliveryCacheManager.saveSession(sessionMessage, PERIODIC_CACHE)

        verify {
            cacheService.writeSession(
                "$prefix.$clockInit.test_cache.json",
                sessionMessage
            )
        }

        assertSessionsMatch(sessionMessage, checkNotNull(deliveryCacheManager.loadSession("test_cache")))

        val expectedByteArray = serializer.toJson(sessionMessage).toByteArray()
        assertArrayEquals(expectedByteArray, checkNotNull(deliveryCacheManager.loadSessionBytes("test_cache")))
    }

    @Test
    fun `cache session on crash successful`() {
        val sessionMessage = createSessionMessage("test_cache")

        deliveryCacheManager.saveSession(sessionMessage, JVM_CRASH)

        verify {
            cacheService.writeSession(
                "$prefix.$clockInit.test_cache.json",
                sessionMessage
            )
        }

        assertSessionsMatch(sessionMessage, checkNotNull(deliveryCacheManager.loadSession("test_cache")))

        val expectedByteArray = serializer.toJson(sessionMessage).toByteArray()
        assertArrayEquals(expectedByteArray, checkNotNull(deliveryCacheManager.loadSessionBytes("test_cache")))
    }

    @Test
    fun `session not found in cache`() {
        assertNull(deliveryCacheManager.loadSession("not_found_session"))
        assertNull(deliveryCacheManager.loadSessionBytes("not_found_session"))
    }

    @Test
    fun `manager returns null if cache service throws an exception`() {
        every { cacheService.loadObject(any(), SessionMessage::class.java) } throws Exception()

        deliveryCacheManager.saveSession(createSessionMessage("exception_session"), NORMAL_END)
        assertNull(deliveryCacheManager.loadSession("exception_session"))

        every {
            cacheService.loadObject(
                any(),
                SessionMessage::class.java
            )
        } answers { callOriginal() }
    }

    @Test
    fun `return serialized current session even if cache fails`() {
        every { cacheService.cacheBytes(any(), any()) } throws Exception()

        val sessionMessage = createSessionMessage("test_cache_fails")
        val expectedBytes = serializer.toJson(sessionMessage).toByteArray()

        val serialized = checkNotNull(deliveryCacheManager.saveSession(sessionMessage, NORMAL_END))

        val charset = Charset.defaultCharset()
        val expectedStr = String(expectedBytes, charset)
        val observedStr = String(serialized)
        assertEquals(expectedStr, observedStr)

        every { cacheService.cacheBytes(any(), any()) } answers { callOriginal() }
    }

    @Test
    fun `remove cached session successfully`() {
        assertNull(deliveryCacheManager.loadSession("test_remove"))

        val session = createSessionMessage("test_remove")
        deliveryCacheManager.saveSession(session, NORMAL_END)

        val cachedSession = deliveryCacheManager.loadSession("test_remove")
        checkNotNull(cachedSession)
        assertSessionsMatch(session, cachedSession)

        deliveryCacheManager.deleteSession("test_remove")

        verify(exactly = 1) { cacheService.deleteFile("$prefix.$clockInit.test_remove.json") }
    }

    @Test
    fun `if an exception is thrown, then remove cache session should not fail`() {
        every { cacheService.deleteFile(any()) } throws Exception()

        deliveryCacheManager.saveSession(createSessionMessage("test_delete_exception"), NORMAL_END)
        deliveryCacheManager.deleteSession("test_delete_exception")

        verify {
            cacheService.deleteFile(
                getCachedSessionName(
                    "test_delete_exception",
                    1663800000000
                )
            )
        }

        every { cacheService.deleteFile(any()) } answers { callOriginal() }
    }

    @Test
    fun `read cached sessions`() {
        cacheService.cacheBytes(
            getCachedSessionName("session1", clockInit - 300000),
            "{ cached_session }".toByteArray()
        )
        cacheService.cacheBytes(
            getCachedSessionName("session2", clockInit - 360000),
            "{ cached_session }".toByteArray()
        )
        cacheService.cacheBytes(
            getCachedSessionName("session3", clockInit - 420000),
            "{ cached_session }".toByteArray()
        )

        assertEquals(
            setOf("session1", "session2", "session3"),
            deliveryCacheManager.getAllCachedSessionIds().toSet()
        )
    }

    @Test
    fun `malformed file names do not trigger an exception`() {
        cacheService.cacheBytes("$prefix.session1.json", "{ cached_session }".toByteArray())
        cacheService.cacheBytes("$prefix.$clockInit.json", "{ cached_session }".toByteArray())
        cacheService.cacheBytes("$prefix..json", "{ cached_session }".toByteArray())
        cacheService.cacheBytes(
            "$prefix.session1.$clockInit.json",
            "{ cached_session }".toByteArray()
        )

        assertTrue(deliveryCacheManager.getAllCachedSessionIds().isEmpty())
    }

    @Test
    fun `amount of cached sessions in file is limited`() {
        repeat(100) { i ->
            deliveryCacheManager.saveSession(createSessionMessage("test$i"), NORMAL_END)
            fakeClock.tick()
        }
        for (i in 0..99) {
            verify(exactly = 1) {
                cacheService.cacheBytes(
                    eq(
                        getCachedSessionName(
                            "test$i",
                            clockInit + i
                        )
                    ),
                    any()
                )
            }
        }
        for (i in 0..(99 - EmbraceDeliveryCacheManager.MAX_SESSIONS_CACHED)) {
            verify(exactly = 1) { cacheService.deleteFile("$prefix.${clockInit + i}.test$i.json") }
        }

        val cachedSessions = deliveryCacheManager.getAllCachedSessionIds()
        assertEquals(EmbraceDeliveryCacheManager.MAX_SESSIONS_CACHED, cachedSessions.size)
        for (i in (100 - EmbraceDeliveryCacheManager.MAX_SESSIONS_CACHED)..99) {
            assertTrue(cachedSessions.contains("test$i"))
        }
    }

    @Test
    fun `check for a session saved in previous versions of the SDK`() {
        val session = createSessionMessage("previous_sdk_session")
        cacheService.cacheObject("last_session.json", session, SessionMessage::class.java)

        initializeSessionCacheManager()

        val allSessions = deliveryCacheManager.getAllCachedSessionIds()
        assertEquals(1, allSessions.size)
        assertSessionsMatch(session, checkNotNull(deliveryCacheManager.loadSession(allSessions[0])))

        verify { cacheService.deleteFile("last_session.json") }
    }

    @Test
    fun `save and load payloads`() {
        val payload = "{ json payload }".toByteArray()
        val cacheName = deliveryCacheManager.savePayload(payload)

        verify { cacheService.cacheBytes(cacheName, payload) }

        assertArrayEquals(payload, deliveryCacheManager.loadPayload(cacheName))
    }

    @Test
    fun `save and load pending api calls`() {
        val pendingApiCalls = PendingApiCalls()
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_1.json", fakeClock.now())
        pendingApiCalls.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        fakeClock.tickSecond()
        val pendingApiCall2 = PendingApiCall(request2, "payload_2.json", fakeClock.now())
        pendingApiCalls.add(pendingApiCall2)

        val request3 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/logging"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id",
            deviceId = "test_device_id",
            eventId = "request_3",
            contentEncoding = "gzip"
        )
        fakeClock.tickSecond()
        val pendingApiCall3 = PendingApiCall(request3, "payload_3.json", fakeClock.now())
        pendingApiCalls.add(pendingApiCall3)

        deliveryCacheManager.savePendingApiCalls(pendingApiCalls)
        val cachedCalls = deliveryCacheManager.loadPendingApiCalls()

        assertEquals(pendingApiCall1, cachedCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall2, cachedCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall3, cachedCalls.pollNextPendingApiCall())
        assertNull(cachedCalls.pollNextPendingApiCall())
    }

    /**
     * The current version is storing [PendingApiCalls] in a file, but previous versions
     * were storing [PendingApiCallsQueue]. This test checks that the current
     * version can read the old version and convert it to the new one.
     * Test that the load works even if the cache returns null when loading the file
     */
    @Test
    fun `load old version of pending api calls file as new version when load cache returns null`() {
        val pendingApiCallsQueue = PendingApiCallsQueue()
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_1.json", fakeClock.now())
        pendingApiCallsQueue.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        fakeClock.tickSecond()
        val pendingApiCall2 = PendingApiCall(request2, "payload_2.json", fakeClock.now())
        pendingApiCallsQueue.add(pendingApiCall2)

        cacheService.cacheObject(
            PENDING_API_CALLS_FILE_NAME,
            pendingApiCallsQueue,
            PendingApiCallsQueue::class.java
        )

        every { cacheService.loadObject(PENDING_API_CALLS_FILE_NAME, PendingApiCalls::class.java) } returns null

        val cachedCalls = deliveryCacheManager.loadPendingApiCalls()
        assertEquals(pendingApiCall1, cachedCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall2, cachedCalls.pollNextPendingApiCall())
        assertEquals(null, cachedCalls.pollNextPendingApiCall())
    }

    /**
     * The current version is storing [PendingApiCalls] in a file, but previous versions
     * were storing [PendingApiCallsQueue]. This test checks that the current
     * version can read the old version and convert it to the new one.
     * Test that the load works even if the cache throws an exception when loading the file.
     */
    @Test
    fun `load old version of pending api calls file as new version when load cache throws exception`() {
        val pendingApiCallsQueue = PendingApiCallsQueue()
        val request1 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/sessions"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id_1",
            deviceId = "test_device_id",
            eventId = "request_1",
            contentEncoding = "gzip"
        )
        val pendingApiCall1 = PendingApiCall(request1, "payload_1.json", fakeClock.now())
        pendingApiCallsQueue.add(pendingApiCall1)

        val request2 = ApiRequest(
            url = EmbraceUrl.create("http://test.url/events"),
            httpMethod = HttpMethod.POST,
            appId = "test_app_id",
            deviceId = "test_device_id",
            eventId = "request_2",
            contentEncoding = "gzip"
        )
        fakeClock.tickSecond()
        val pendingApiCall2 = PendingApiCall(request2, "payload_2.json", fakeClock.now())
        pendingApiCallsQueue.add(pendingApiCall2)

        cacheService.cacheObject(
            PENDING_API_CALLS_FILE_NAME,
            pendingApiCallsQueue,
            PendingApiCallsQueue::class.java
        )

        every { cacheService.loadObject(PENDING_API_CALLS_FILE_NAME, PendingApiCalls::class.java) } throws Exception()

        val cachedCalls = deliveryCacheManager.loadPendingApiCalls()
        assertEquals(pendingApiCall1, cachedCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall2, cachedCalls.pollNextPendingApiCall())
        assertEquals(null, cachedCalls.pollNextPendingApiCall())
    }

    @Test
    fun `load empty set of delivery calls if non cached`() {
        val pendingApiCalls = deliveryCacheManager.loadPendingApiCalls()
        assertFalse(pendingApiCalls.hasAnyPendingApiCall())
    }

    private fun assertSessionsMatch(session1: SessionMessage, session2: SessionMessage) {
        // SessionMessage does not implement equals, so we have to serialize to compare
        assertEquals(
            serializer.toJson(session1),
            serializer.toJson(session2)
        )
    }

    private fun createSessionMessage(sessionId: String): SessionMessage {
        val session = fakeSession().copy(
            sessionId = sessionId,
            startTime = fakeClock.now()
        )
        return SessionMessage(session)
    }

    private fun getCachedSessionName(sessionId: String, timestamp: Long): String {
        return EmbraceDeliveryCacheManager.CachedSession(sessionId, timestamp).filename
    }
}

private const val PENDING_API_CALLS_FILE_NAME = "failed_api_calls.json"
