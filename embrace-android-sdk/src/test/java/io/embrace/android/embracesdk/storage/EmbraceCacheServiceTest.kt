package io.embrace.android.embracesdk.storage

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.fakeSessionEnvelope
import io.embrace.android.embracesdk.fixtures.testSessionEnvelope
import io.embrace.android.embracesdk.fixtures.testSessionEnvelope2
import io.embrace.android.embracesdk.fixtures.testSessionEnvelopeOneMinuteLater
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.getStartTime
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiRequestUrl
import io.embrace.android.embracesdk.internal.comms.delivery.CacheService
import io.embrace.android.embracesdk.internal.comms.delivery.CachedSession
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService.Companion.EMBRACE_PREFIX
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService.Companion.NEW_COPY_SUFFIX
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService.Companion.OLD_COPY_SUFFIX
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService.Companion.TEMP_COPY_SUFFIX
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.network.http.HttpMethod
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
    private lateinit var logger: FakeEmbLogger
    private val serializer = TestPlatformSerializer()

    @Before
    fun setUp() {
        storageManager = FakeStorageService()
        logger = FakeEmbLogger()
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
    fun `cacheObject in non-writable directory and file does not throw exception`() {
        val directory = File(storageManager.filesDirectory, "")
        val cacheFile = File(storageManager.filesDirectory, "emb_$CUSTOM_OBJECT_1_FILE_NAME")
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, "old data", String::class.java)
        assertEquals("old data", service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, String::class.java))
        directory.setReadOnly()
        cacheFile.setReadOnly()

        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, "data", String::class.java)
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, "tha new new", String::class.java)
        assertEquals("old data", service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, String::class.java))
    }

    @Test
    fun `test cacheObject and loadObject`() {
        val myObject = fakeSessionEnvelope()
        val type = Envelope.sessionEnvelopeType
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, type)
        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val loadedObject = service.loadObject<Envelope<SessionPayload>>(CUSTOM_OBJECT_1_FILE_NAME, type)
        assertEquals(myObject.getSessionId(), checkNotNull(loadedObject).getSessionId())
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
    fun `test loadObject with non-existent file returns null`() {
        assertNull(service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, Envelope.sessionEnvelopeType))
    }

    @Test
    fun `test loadObject with malformed file returns null`() {
        val myObject1 = fakeSessionEnvelope()
        val type = Envelope.sessionEnvelopeType
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject1, type)

        val children = checkNotNull(storageManager.filesDirectory.listFiles())
        val file = children.single()
        file.writeText("malformed content")

        val loadedObject = service.loadObject<Envelope<SessionPayload>>(CUSTOM_OBJECT_1_FILE_NAME, type)
        assertNull(loadedObject)
    }

    @Test
    fun `test PendingApiCalls can be cached`() {
        val apiRequest = ApiRequest(
            httpMethod = HttpMethod.GET,
            url = ApiRequestUrl("http://fake.url/sessions")
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
            service.loadObject<PendingApiCalls>(cacheKey, PendingApiCalls::class.java)

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
        val original = fakeSessionEnvelope()
        service.writeSession("test", original)
        val loadObject = service.loadObject<Envelope<SessionPayload>>("test", Envelope.sessionEnvelopeType)
        assertEquals(original.getSessionId(), loadObject?.getSessionId())
    }

    @Test
    fun `test deleteFile from files dir`() {
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, "data", String::class.java)

        var children = checkNotNull(storageManager.filesDirectory.listFiles())
        assertEquals(1, children.size)
        service.deleteFile(CUSTOM_OBJECT_1_FILE_NAME)
        children = checkNotNull(storageManager.filesDirectory.listFiles())
        assertEquals(0, children.size)
    }

    @Test
    fun `only proper session file IDs returned when normalizeCacheAndGetSessionFileIds is called`() {
        val session1 = testSessionEnvelope
        val session2 = testSessionEnvelope2
        val session1FileName = CachedSession.create(session1.getSessionId(), session1.getStartTime(), false).filename
        val session2FileName = CachedSession.create(session2.getSessionId(), session2.getStartTime(), false).filename
        service.writeSession(session1FileName, session1)
        service.writeSession(session2FileName, session2)
        service.writeSession("not-match.json", testSessionEnvelope)

        val filenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(3, storageManager.listFiles().size)
        assertEquals(2, filenames.size)
        assertTrue(filenames.contains(session1FileName))
        assertTrue(filenames.contains(session1FileName))
    }

    @Test
    fun `session file IDs with unexpected timestamp returned when normalizeCacheAndGetSessionFileIds is called`() {
        val session = testSessionEnvelope
        val sessionFileNameWithUnexpectedTs =
            CachedSession.create(session.getSessionId(), session.getStartTime() + 10000L, false).filename
        service.writeSession(sessionFileNameWithUnexpectedTs, session)

        val filenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(1, storageManager.listFiles().size)
        assertEquals(1, filenames.size)
        assertTrue(filenames.contains(sessionFileNameWithUnexpectedTs))
    }

    @Test
    fun `temp files removed during cache normalization`() {
        val session1 = testSessionEnvelope
        val session2 = testSessionEnvelope2
        val session1FileName = CachedSession.create(session1.getSessionId(), session1.getStartTime(), false).filename
        val session2FileName = CachedSession.create(session2.getSessionId(), session2.getStartTime(), false).filename
        val badSessionFileName = CachedSession.create("badId", System.currentTimeMillis(), false).filename
        service.writeSession(session1FileName, session1)
        service.writeSession(session2FileName + OLD_COPY_SUFFIX, session2)
        service.writeSession(badSessionFileName + TEMP_COPY_SUFFIX, testSessionEnvelopeOneMinuteLater)
        assertEquals(3, storageManager.listFiles().size)

        val filenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(1, storageManager.listFiles().size)
        assertEquals(1, filenames.size)
        assertTrue(filenames.contains(session1FileName))
    }

    @Test
    fun `new version of session file replaces existing one during cache normalization`() {
        val session = testSessionEnvelope
        val newSession = testSessionEnvelopeOneMinuteLater
        val sessionFileName = CachedSession.create(session.getSessionId(), session.getStartTime(), false).filename
        service.writeSession(sessionFileName, session)
        service.writeSession(sessionFileName + NEW_COPY_SUFFIX, newSession)

        val type = Envelope.sessionEnvelopeType
        val rawFilenames = storageManager.listFiles().map { it.name }
        assertEquals(2, rawFilenames.size)
        assertTrue(rawFilenames.contains(EMBRACE_PREFIX + sessionFileName))
        assertTrue(rawFilenames.contains(EMBRACE_PREFIX + sessionFileName + NEW_COPY_SUFFIX))
        assertEquals(session.getSessionId(), service.loadObject<Envelope<SessionPayload>>(sessionFileName, type)?.getSessionId())

        val sessionFilenames = service.normalizeCacheAndGetSessionFileIds()
        assertEquals(1, storageManager.listFiles().size)
        assertEquals(1, sessionFilenames.size)
        assertTrue(sessionFilenames.contains(sessionFileName))
        assertEquals(newSession.getSessionId(), service.loadObject<Envelope<SessionPayload>>(sessionFileName, type)?.getSessionId())
    }

    @Test
    fun `new version of session file swapped in during cache normalization if proper session file is missing`() {
        val session = testSessionEnvelope
        val sessionFileName = CachedSession.create(session.getSessionId(), session.getStartTime(), false).filename
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
                    url = ApiRequestUrl("http://fake.url/sessions")
                ),
                "payload_id_1"
            ),
            PendingApiCall(
                ApiRequest(
                    httpMethod = HttpMethod.POST,
                    url = ApiRequestUrl("http://fake.url/sessions")
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
        val original = testSessionEnvelope
        val filename = CachedSession.create(
            sessionId = original.getSessionId(),
            timestampMs = original.getStartTime(),
            v2Payload = false
        ).filename
        val replacement = testSessionEnvelopeOneMinuteLater

        service.writeSession(filename, original)

        val clz = Envelope.sessionEnvelopeType
        val files = storageManager.listFiles { _, _ -> true }
        assertEquals(1, files.size)
        assertEquals(service.loadObject<Envelope<SessionPayload>>(filename, clz)?.getSessionId(), original.getSessionId())

        service.transformSession(filename) { replacement }
        assertEquals(service.loadObject<Envelope<SessionPayload>>(filename, clz)?.getSessionId(), replacement.getSessionId())

        val filesAgain = storageManager.listFiles { _, _ -> true }
        assertEquals(1, filesAgain.size)
        assertEquals(files[0], filesAgain[0])

        val errors = logger.errorMessages
        assertEquals("The following errors were logged: $errors", 0, logger.errorMessages.size)
    }

    @Test
    fun `test is v2 payload`() {
        val mgs = fakeSessionEnvelope()
        val filename = CachedSession.create(mgs.getSessionId(), mgs.getStartTime(), false).filename
        assertFalse(checkNotNull(CachedSession.fromFilename(filename)).v2Payload)
        val v2Filename = filename.replace(".json", ".v2.json")
        assertTrue(checkNotNull(CachedSession.fromFilename(v2Filename)).v2Payload)
    }

    companion object {
        private const val CUSTOM_OBJECT_1_FILE_NAME = "custom_object_1.json"
    }
}
