package io.embrace.android.embracesdk

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.delivery.CachedSession
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fixtures.testSessionMessage
import io.embrace.android.embracesdk.fixtures.testSessionMessageOneMinuteLater
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.JVM_CRASH
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType.PERIODIC_CACHE
import io.embrace.android.embracesdk.storage.StorageService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal class EmbraceDeliveryCacheManagerTest {

    private val prefix = "last_session"
    private val serializer = EmbraceSerializer()
    private val worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())
    private lateinit var deliveryCacheManager: EmbraceDeliveryCacheManager
    private lateinit var storageService: StorageService
    private lateinit var cacheService: EmbraceCacheService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var fakeClock: FakeClock

    companion object {
        private const val clockInit = 1663800000000
    }

    @Before
    fun before() {
        fakeClock = FakeClock(clockInit)
        logger = InternalEmbraceLogger()
        storageService = FakeStorageService()
        cacheService = spyk(
            EmbraceCacheService(
                storageService = storageService,
                serializer = serializer,
                logger = logger
            )
        )
        deliveryCacheManager = EmbraceDeliveryCacheManager(
            cacheService,
            worker,
            logger,
            fakeClock
        )
    }

    @After
    fun after() {
        clearAllMocks()
    }

    @Test
    fun `serializing current session successfully`() {
        val sessionMessage = createSessionMessage("test_cache")
        deliveryCacheManager.saveSession(sessionMessage, NORMAL_END)
        assertNotNull(deliveryCacheManager.loadSessionAsAction("test_cache"))
    }

    @Test
    fun `cache periodic session successful`() {
        val sessionMessage = createSessionMessage("test_cache")
        deliveryCacheManager.saveSession(sessionMessage, PERIODIC_CACHE)
        assertNotNull(deliveryCacheManager.loadSessionAsAction("test_cache"))
    }

    @Test
    fun `cache session on crash successful`() {
        val sessionMessage = createSessionMessage("test_cache")
        deliveryCacheManager.saveSession(sessionMessage, JVM_CRASH)
        assertNotNull(deliveryCacheManager.loadSessionAsAction("test_cache"))
    }

    @Test
    fun `session not found in cache`() {
        assertNull(deliveryCacheManager.loadSessionAsAction("not_found_session"))
    }

    @Test
    fun `could not load session if cache service throws an exception when writing the initial session`() {
        every { cacheService.writeSession(any(), any()) } throws Exception()

        deliveryCacheManager.saveSession(createSessionMessage("exception_session"), NORMAL_END)
        assertNull(deliveryCacheManager.loadSessionAsAction("exception_session"))
    }

    @Test
    fun `return serialized current session even if cache fails`() {
        every { cacheService.writeSession(any(), eq(testSessionMessageOneMinuteLater)) } throws Exception()
        deliveryCacheManager.saveSession(testSessionMessage, PERIODIC_CACHE)
        val original = ByteArrayOutputStream()
        original.use(checkNotNull(deliveryCacheManager.loadSessionAsAction(testSessionMessage.session.sessionId)))

        deliveryCacheManager.saveSession(testSessionMessageOneMinuteLater, PERIODIC_CACHE)

        val secondLoad = ByteArrayOutputStream()
        secondLoad.use(checkNotNull(deliveryCacheManager.loadSessionAsAction(testSessionMessage.session.sessionId)))

        assertArrayEquals(original.toByteArray(), secondLoad.toByteArray())
    }

    @Test
    fun `remove cached session successfully`() {
        assertNull(deliveryCacheManager.loadSessionAsAction("test_remove"))

        val session = createSessionMessage("test_remove")
        deliveryCacheManager.saveSession(session, NORMAL_END)
        assertNotNull(deliveryCacheManager.loadSessionAsAction("test_remove"))

        deliveryCacheManager.deleteSession("test_remove")
        assertNull(deliveryCacheManager.loadSessionAsAction("test_remove"))
    }

    @Test
    fun `if an exception is thrown when deleting a file, the operation should not throw an operation`() {
        every { cacheService.deleteFile(any()) } throws Exception()

        deliveryCacheManager.saveSession(createSessionMessage("test_delete_exception"), NORMAL_END)
        deliveryCacheManager.deleteSession("deliveryCacheManager")
        assertNotNull(deliveryCacheManager.loadSessionAsAction("test_delete_exception"))
    }

    @Test
    fun `read cached sessions`() {
        cacheService.writeSession(
            CachedSession.create("session1", clockInit - 300000, false).filename,
            testSessionMessage.copy(session = testSessionMessage.session.copy(sessionId = "session1", startTime = clockInit - 300000))
        )
        cacheService.writeSession(
            CachedSession.create("session2", clockInit - 360000, false).filename,
            testSessionMessage.copy(session = testSessionMessage.session.copy(sessionId = "session2", startTime = clockInit - 360000))
        )
        cacheService.writeSession(
            CachedSession.create("session3", clockInit - 420000, false).filename,
            testSessionMessage.copy(session = testSessionMessage.session.copy(sessionId = "session3", startTime = clockInit - 420000))
        )
        assertEquals(
            setOf("session1", "session2", "session3"),
            deliveryCacheManager.getAllCachedSessionIds().map(CachedSession::sessionId).toSet()
        )
    }

    @Test
    fun `malformed file names do not trigger an exception`() {
        cacheService.writeSession("$prefix.session1.json", testSessionMessage)
        cacheService.writeSession("$prefix.$clockInit.json", testSessionMessage)
        cacheService.writeSession("$prefix..json", testSessionMessage)
        cacheService.writeSession("$prefix.session1.$clockInit.json", testSessionMessage)

        assertTrue(deliveryCacheManager.getAllCachedSessionIds().isEmpty())
    }

    @Test
    fun `amount of cached sessions in file is limited`() {
        repeat(100) { i ->
            deliveryCacheManager.saveSession(createSessionMessage("test$i"), NORMAL_END)
            fakeClock.tick()
        }

        val cachedSessions = deliveryCacheManager.getAllCachedSessionIds().map(CachedSession::sessionId)
        assertEquals(EmbraceDeliveryCacheManager.MAX_SESSIONS_CACHED, cachedSessions.size)
        for (i in (100 - EmbraceDeliveryCacheManager.MAX_SESSIONS_CACHED)..99) {
            assertTrue(cachedSessions.contains("test$i"))
        }
    }

    @Test
    fun `check for a session saved in previous versions of the SDK will return and the original file will be deleted`() {
        val session = testSessionMessage
        cacheService.writeSession("last_session.json", session)

        val allSessions = deliveryCacheManager.getAllCachedSessionIds()
        assertEquals(1, allSessions.size)
        assertNotNull(deliveryCacheManager.loadSessionAsAction(allSessions[0].sessionId))
        assertNull(cacheService.loadObject("last_session.json", SessionMessage::class.java))
    }

    @Test
    fun `load uncompressed payload as action that will output compressed bytes`() {
        val payload = "{ json payload }".toByteArray()
        val action: (outputStream: OutputStream) -> Unit = {
            it.write(payload)
        }
        val cacheName = deliveryCacheManager.savePayload(action)

        val returnedAction = checkNotNull(deliveryCacheManager.loadPayloadAsAction(cacheName))
        val stream = ByteArrayOutputStream()
        returnedAction(stream)
        val compressed = stream.toByteArray()
        val uncompressed = GZIPInputStream(compressed.inputStream()).readBytes()
        assertArrayEquals(payload, uncompressed)
    }

    @Test
    fun `load compressed payload as action that will output compressed bytes`() {
        val gzippedBytes = ByteArrayOutputStream().use { byteArrayStream ->
            GZIPOutputStream(byteArrayStream).use { gzipStream ->
                gzipStream.write("{ json payload }".toByteArray())
                gzipStream.finish()
            }
            byteArrayStream.toByteArray()
        }
        val action: (outputStream: OutputStream) -> Unit = {
            it.write(gzippedBytes)
        }
        val cacheName = deliveryCacheManager.savePayload(action)

        val returnedAction = checkNotNull(deliveryCacheManager.loadPayloadAsAction(cacheName))
        val stream = ByteArrayOutputStream()
        returnedAction(stream)
        val compressed = stream.toByteArray()
        assertArrayEquals(gzippedBytes, compressed)
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
     * were storing a list of [PendingApiCall]. This test checks that the current
     * version can read the old version and convert it to the new one.
     * Test that the load works even if the cache returns null when loading the file
     */
    @Test
    fun `load old version of pending api calls file as new version when load cache returns null`() {
        val pendingApiCallsQueue = mutableListOf<PendingApiCall>()
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

        cacheService.cacheObject("failed_api_calls.json", pendingApiCallsQueue, List::class.java)

        val cachedCalls = deliveryCacheManager.loadPendingApiCalls()
        assertEquals(pendingApiCall1, cachedCalls.pollNextPendingApiCall())
        assertEquals(pendingApiCall2, cachedCalls.pollNextPendingApiCall())
        assertEquals(null, cachedCalls.pollNextPendingApiCall())
    }

    @Test
    fun `load empty set of delivery calls if non cached`() {
        val pendingApiCalls = deliveryCacheManager.loadPendingApiCalls()
        assertFalse(pendingApiCalls.hasPendingApiCallsToSend())
    }

    private fun createSessionMessage(sessionId: String): SessionMessage {
        val session = fakeSession().copy(
            sessionId = sessionId,
            startTime = fakeClock.now()
        )
        return SessionMessage(session)
    }
}
