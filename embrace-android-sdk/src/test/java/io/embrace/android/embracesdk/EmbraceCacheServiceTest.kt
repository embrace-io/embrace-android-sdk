package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.CachedSession
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService.Companion.EMBRACE_PREFIX
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService.Companion.NEW_COPY_SUFFIX
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService.Companion.OLD_COPY_SUFFIX
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService.Companion.TEMP_COPY_SUFFIX
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fixtures.testSessionMessage
import io.embrace.android.embracesdk.fixtures.testSessionMessage2
import io.embrace.android.embracesdk.fixtures.testSessionMessageOneMinuteLater
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal class EmbraceCacheServiceTest {

    private lateinit var service: CacheService
    private lateinit var storageManager: FakeStorageService
    private lateinit var loggerAction: FakeLoggerAction
    private lateinit var logger: InternalEmbraceLogger
    private val serializer = TestPlatformSerializer()

    @Before
    fun setUp() {
        storageManager = FakeStorageService()
        loggerAction = FakeLoggerAction()
        logger = InternalEmbraceLogger().apply { addLoggerAction(loggerAction) }
        service = EmbraceCacheService(
            storageManager,
            serializer,
            logger
        )

        // always assert that nothing is in the dir
        assertTrue(checkNotNull(storageManager.cacheDirectory.listFiles()).isEmpty())
        assertTrue(checkNotNull(storageManager.filesDirectory.listFiles()).isEmpty())
    }

    @Test
    fun `test cacheBytes and loadBytes`() {
        val myBytes = "{ \"payload\": \"test_payload\"}".toByteArray()
        service.cacheBytes(CUSTOM_OBJECT_1_FILE_NAME, myBytes)
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val loadedObject = service.loadBytes(CUSTOM_OBJECT_1_FILE_NAME)
        assertArrayEquals(myBytes, loadedObject)
    }

    @Test
    fun `test loadBytes with non-existent file returns empty optional`() {
        val loadedBytes = service.loadBytes(CUSTOM_OBJECT_1_FILE_NAME)
        assertNull(loadedBytes)
    }

    @Test
    fun `test cacheBytes with non-writable file does not throw exception`() {
        val cacheFile = File(storageManager.filesDirectory, "emb_$CUSTOM_OBJECT_1_FILE_NAME")
        cacheFile.writeText("locked file")
        cacheFile.setReadOnly()

        val myBytes = "{ \"payload\": \"test_payload\"}".toByteArray()
        service.cacheBytes(CUSTOM_OBJECT_1_FILE_NAME, myBytes)

        val loadedBytes = service.loadBytes(CUSTOM_OBJECT_1_FILE_NAME)
        assertNull(loadedBytes)
    }

    @Test
    fun `test cacheObject and loadObject`() {
        val myObject = fakeSession()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, Session::class.java)
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val loadedObject = service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, Session::class.java)
        assertEquals(myObject, checkNotNull(loadedObject))
    }

    @Test
    fun `test cachePayload stores uncompressed data and loadPayload returns compressed data`() {
        service.cachePayload(CUSTOM_OBJECT_1_FILE_NAME) { it.write("test".toByteArray()) }
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val action = checkNotNull(service.loadPayload(CUSTOM_OBJECT_1_FILE_NAME))
        val stream = ByteArrayOutputStream()
        action(stream)
        val compressed = stream.toByteArray()
        val uncompressed = String(GZIPInputStream(compressed.inputStream()).readBytes())
        assertEquals("test", uncompressed)
    }

    @Test
    fun `test cachePayload stores compressed data and loadPayload returns compressed data`() {
        service.cachePayload(CUSTOM_OBJECT_1_FILE_NAME) {
            GZIPOutputStream(it.buffered()).use { gzipStream ->
                gzipStream.write("test".toByteArray())
            }
        }
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val action = checkNotNull(service.loadPayload(CUSTOM_OBJECT_1_FILE_NAME))
        val stream = ByteArrayOutputStream()
        action(stream)
        val compressed = stream.toByteArray()
        val uncompressed = String(GZIPInputStream(compressed.inputStream()).readBytes())
        assertEquals("test", uncompressed)
    }

    @Test
    fun `cachePayload action throws exception`() {
        service.cachePayload(CUSTOM_OBJECT_1_FILE_NAME) { error("Whoops") }
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        assertTrue(children.isEmpty())
    }

    @Test
    fun `test loadPayload with non-existent file returns empty string in the output stream`() {
        val action = service.loadPayload("some_file.jpeg")
        val stream = ByteArrayOutputStream()
        action(stream)
        val result = String(stream.toByteArray().inputStream().readBytes())
        assertEquals("", result)
    }

    @Test
    fun `test loadObject with non-existent file returns empty optional`() {
        val loadedObject = service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, Session::class.java)
        assertNull(loadedObject)
    }

    @Test
    fun `test loadObject with malformed file returns empty optional`() {
        val myObject1 = fakeSession()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject1, Session::class.java)

        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        file.writeText("malformed content")

        val loadedObject = service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, Session::class.java)
        assertNull(loadedObject)
    }

    @Test
    fun `test PendingApiCalls can be cached`() {
        val apiRequest = ApiRequest(
            httpMethod = HttpMethod.GET,
            url = EmbraceUrl.create("http://fake.url/sessions")
        )
        val pendingApiCalls = PendingApiCalls()
        pendingApiCalls.add(PendingApiCall(apiRequest, "payload_id"))

        val cacheKey = "test_pending_calls_cache"
        service.cacheObject(
            cacheKey,
            pendingApiCalls,
            PendingApiCalls::class.java
        )
        val cachedPendingCalls =
            service.loadObject(cacheKey, PendingApiCalls::class.java)

        checkNotNull(cachedPendingCalls)
        assertTrue(cachedPendingCalls.hasPendingApiCallsToSend())
        val cachedApiRequest = cachedPendingCalls.pollNextPendingApiCall()?.apiRequest
        assertNotNull(cachedApiRequest)
        assertEquals(apiRequest.contentType, cachedApiRequest?.contentType)
        assertEquals(apiRequest.userAgent, cachedApiRequest?.userAgent)
        assertEquals(apiRequest.contentEncoding, cachedApiRequest?.contentEncoding)
        assertEquals(apiRequest.accept, cachedApiRequest?.accept)
        assertEquals(apiRequest.acceptEncoding, cachedApiRequest?.acceptEncoding)
        assertEquals(apiRequest.appId, cachedApiRequest?.appId)
        assertEquals(apiRequest.deviceId, cachedApiRequest?.deviceId)
        assertEquals(apiRequest.eventId, cachedApiRequest?.eventId)
        assertEquals(apiRequest.logId, cachedApiRequest?.logId)
        assertEquals(apiRequest.url.toString(), cachedApiRequest?.url.toString())
        assertEquals(apiRequest.httpMethod, cachedApiRequest?.httpMethod)
    }

    @Test
    fun `test write session`() {
        val original = SessionMessage(fakeSession())
        service.writeSession("test", original)
        val loadObject = service.loadObject("test", SessionMessage::class.java)
        assertEquals(original, loadObject)
    }

    @Test
    fun `test deleteFile from files dir`() {
        val myBytes = "{ \"payload\": \"test_payload\"}".toByteArray()
        service.cacheBytes(CUSTOM_OBJECT_1_FILE_NAME, myBytes)

        var children = checkNotNull(storageManager.filesDirectory.listFiles())
        assertEquals(1, children.size)
        service.deleteFile(CUSTOM_OBJECT_1_FILE_NAME)
        children = checkNotNull(storageManager.filesDirectory.listFiles())
        assertEquals(0, children.size)
    }

    @Test
    fun `only proper session file IDs returned when normalizeCacheAndGetSessionFileIds is called`() {
        val session1 = testSessionMessage
        val session2 = testSessionMessage2
        val session1FileName = CachedSession.create(session1.session.sessionId, session1.session.startTime, false).filename
        val session2FileName = CachedSession.create(session2.session.sessionId, session2.session.startTime, false).filename
        service.writeSession(session1FileName, session1)
        service.writeSession(session2FileName, session2)
        service.cacheBytes("not-match.json", testPayloadBytes)

        val filenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(3, storageManager.listFiles().size)
        assertEquals(2, filenames.size)
        assertTrue(filenames.contains(session1FileName))
        assertTrue(filenames.contains(session1FileName))
    }

    @Test
    fun `temp files removed during cache normalization`() {
        val session1 = testSessionMessage
        val session2 = testSessionMessage2
        val session1FileName = CachedSession.create(session1.session.sessionId, session1.session.startTime, false).filename
        val session2FileName = CachedSession.create(session2.session.sessionId, session2.session.startTime, false).filename
        val badSessionFileName = CachedSession.create("badId", System.currentTimeMillis(), false).filename
        service.writeSession(session1FileName, session1)
        service.writeSession(session2FileName + OLD_COPY_SUFFIX, session2)
        service.cacheBytes(badSessionFileName + TEMP_COPY_SUFFIX, testPayloadBytes)
        assertEquals(3, storageManager.listFiles().size)

        val filenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(1, storageManager.listFiles().size)
        assertEquals(1, filenames.size)
        assertTrue(filenames.contains(session1FileName))
    }

    @Test
    fun `new version of session file replaces existing one during cache normalization`() {
        val session = testSessionMessage
        val newSession = testSessionMessageOneMinuteLater
        val sessionFileName = CachedSession.create(session.session.sessionId, session.session.startTime, false).filename
        service.writeSession(sessionFileName, session)
        service.writeSession(sessionFileName + NEW_COPY_SUFFIX, newSession)

        val rawFilenames = storageManager.listFiles().map { it.name }
        assertEquals(2, rawFilenames.size)
        assertTrue(rawFilenames.contains(EMBRACE_PREFIX + sessionFileName))
        assertTrue(rawFilenames.contains(EMBRACE_PREFIX + sessionFileName + NEW_COPY_SUFFIX))
        assertEquals(session, service.loadObject(sessionFileName, SessionMessage::class.java))

        val sessionFilenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(1, storageManager.listFiles().size)
        assertEquals(1, sessionFilenames.size)
        assertTrue(sessionFilenames.contains(sessionFileName))
        assertEquals(newSession, service.loadObject(sessionFileName, SessionMessage::class.java))
    }

    @Test
    fun `new version of session file swapped in during cache normalization if proper session file is missing`() {
        val session = testSessionMessage
        val sessionFileName = CachedSession.create(session.session.sessionId, session.session.startTime, false).filename
        service.writeSession(sessionFileName + NEW_COPY_SUFFIX, session)
        service.writeSession(sessionFileName + OLD_COPY_SUFFIX, session)

        val rawFilenames = storageManager.listFiles().map { it.name }
        assertEquals(2, rawFilenames.size)
        assertTrue(rawFilenames.contains(EMBRACE_PREFIX + sessionFileName + NEW_COPY_SUFFIX))
        assertTrue(rawFilenames.contains(EMBRACE_PREFIX + sessionFileName + OLD_COPY_SUFFIX))

        val sessionFilenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(1, storageManager.listFiles().size)
        assertEquals(1, sessionFilenames.size)
        assertTrue(sessionFilenames.contains(sessionFileName))
    }

    @Test
    fun `test loadOldPendingApiCalls with existing elements`() {
        val myObject = listOf(
            PendingApiCall(
                ApiRequest(
                    httpMethod = HttpMethod.POST,
                    url = EmbraceUrl.create("http://fake.url/sessions")
                ),
                "payload_id_1"
            ),
            PendingApiCall(
                ApiRequest(
                    httpMethod = HttpMethod.POST,
                    url = EmbraceUrl.create("http://fake.url/sessions")
                ),
                "payload_id_2"
            )
        )
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, List::class.java)
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val loadedObject = service.loadOldPendingApiCalls(CUSTOM_OBJECT_1_FILE_NAME)
        assertEquals(myObject, checkNotNull(loadedObject))
        assertEquals(2, loadedObject.size)
        assertEquals(myObject[0], loadedObject[0])
        assertEquals(myObject[1], loadedObject[1])
    }

    @Test
    fun `test loadOldPendingApiCalls with no elements`() {
        val myObject = emptyList<PendingApiCall>()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, List::class.java)
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val loadedObject = service.loadOldPendingApiCalls(CUSTOM_OBJECT_1_FILE_NAME)
        assertEquals(myObject, checkNotNull(loadedObject))
        assertEquals(0, loadedObject.size)
    }

    @Test
    fun `session replacement does not create duplicate files`() {
        val original = testSessionMessage
        val filename = CachedSession.create(
            sessionId = original.session.sessionId,
            timestampMs = original.session.startTime,
            v2Payload = false
        ).filename
        val replacement = testSessionMessageOneMinuteLater

        service.writeSession(filename, original)

        val files = storageManager.listFiles { _, _ -> true }
        assertEquals(1, files.size)
        assertEquals(service.loadObject(filename, SessionMessage::class.java), original)

        service.transformSession(filename) { replacement }
        assertEquals(service.loadObject(filename, SessionMessage::class.java), replacement)

        val filesAgain = storageManager.listFiles { _, _ -> true }
        assertEquals(1, filesAgain.size)
        assertEquals(files[0], filesAgain[0])

        val errors = loggerAction.msgQueue.filter { it.severity == InternalStaticEmbraceLogger.Severity.ERROR }
        assertEquals("The following errors were logged: $errors", 0, errors.size)
    }

    @Test
    fun `test is v2 payload`() {
        val session = fakeSession()
        val filename = CachedSession.create(session.sessionId, session.startTime, false).filename
        assertFalse(checkNotNull(CachedSession.fromFilename(filename)).v2Payload)
        val v2Filename = filename.replace(".json", ".v2.json")
        assertTrue(checkNotNull(CachedSession.fromFilename(v2Filename)).v2Payload)
    }

    companion object {
        private const val CUSTOM_OBJECT_1_FILE_NAME = "custom_object_1.json"
        private const val TEST_PAYLOAD = "this is payload"
        private val testPayloadBytes = TEST_PAYLOAD.toByteArray()
    }
}
