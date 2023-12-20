package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
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
import java.nio.file.Files

internal class EmbraceCacheServiceTest {

    private lateinit var service: CacheService
    private lateinit var dir: File

    private val serializer = EmbraceSerializer()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("tmpDirPrefix").toFile()
        service = EmbraceCacheService(
            lazy { dir },
            serializer,
            InternalEmbraceLogger()
        )

        // always assert that nothing is in the dir
        assertTrue(checkNotNull(dir.listFiles()).isEmpty())
    }

    @Test
    fun `test cacheBytes and loadBytes`() {
        val myBytes = "{ \"payload\": \"test_payload\"}".toByteArray()
        service.cacheBytes(CUSTOM_OBJECT_1_FILE_NAME, myBytes)
        val children = checkNotNull(dir.listFiles())
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
        val cacheFile = File(dir, "emb_$CUSTOM_OBJECT_1_FILE_NAME")
        cacheFile.writeText("locked file")
        cacheFile.setReadOnly()

        val myBytes = "{ \"payload\": \"test_payload\"}".toByteArray()
        service.cacheBytes(CUSTOM_OBJECT_1_FILE_NAME, myBytes)

        val loadedBytes = service.loadBytes(CUSTOM_OBJECT_1_FILE_NAME)
        assertNotNull(loadedBytes)
        assertArrayEquals("locked file".toByteArray(), loadedBytes)
        cacheFile.delete()
    }

    @Test
    fun `test cacheObject and loadObject`() {
        val myObject = fakeSession()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, Session::class.java)
        val children = checkNotNull(dir.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val loadedObject = service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, Session::class.java)
        assertEquals(myObject, checkNotNull(loadedObject))
    }

    @Test
    fun `test cachePayload and loadPayload`() {
        service.cachePayload(CUSTOM_OBJECT_1_FILE_NAME) { it.write("test".toByteArray()) }
        val children = checkNotNull(dir.listFiles())
        val file = children.single()
        assertEquals("emb_$CUSTOM_OBJECT_1_FILE_NAME", file.name)

        val action = checkNotNull(service.loadPayload(CUSTOM_OBJECT_1_FILE_NAME))
        val stream = ByteArrayOutputStream()
        action(stream)
        assertEquals("test", String(stream.toByteArray()))
    }

    @Test
    fun `cachePayload action throws exception`() {
        service.cachePayload(CUSTOM_OBJECT_1_FILE_NAME) { error("Whoops") }
        val children = checkNotNull(dir.listFiles())
        assertTrue(children.isEmpty())
    }

    @Test
    fun `test loadPayload with non-existent file returns null`() {
        val action = service.loadPayload("some_file.jpeg")
        val stream = ByteArrayOutputStream()
        action(stream)
        assertEquals("", String(stream.toByteArray()))
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

        val children = checkNotNull(dir.listFiles())
        val file = children.single()
        file.writeText("malformed content")

        val loadedObject = service.loadObject(CUSTOM_OBJECT_1_FILE_NAME, Session::class.java)
        assertNull(loadedObject)
    }

    @Test
    fun `test deleteObject returns true and deletes the file correctly`() {
        val myObject = fakeSession()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, Session::class.java)

        val deleted = service.deleteObject(CUSTOM_OBJECT_1_FILE_NAME)
        val children = checkNotNull(dir.listFiles())

        assertTrue(deleted)
        assertEquals(0, children.size)
    }

    @Test
    fun `test deleteObject with non-existent file returns false`() {
        val deleted = service.deleteObject(CUSTOM_OBJECT_1_FILE_NAME)
        val children = checkNotNull(dir.listFiles())

        assertFalse(deleted)
        assertEquals(0, children.size)
    }

    @Test
    fun `test deleteObjectsByRegex`() {
        val myObject1 = fakeSession()
        val myObject2 = fakeSession()
        val myObject3 = fakeSession()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject1, Session::class.java)
        service.cacheObject(CUSTOM_OBJECT_2_FILE_NAME, myObject2, Session::class.java)
        service.cacheObject(CUSTOM_OBJECT_3_FILE_NAME, myObject3, Session::class.java)

        val deleted = service.deleteObjectsByRegex(".*object.*")
        val children = checkNotNull(dir.listFiles())

        assertTrue(deleted)
        assertEquals(1, children.size)
    }

    @Test
    fun `test deleteObjectsByRegex with listFiles() = null returns false`() {
        // In order to force File.listFiles() to return null, we make the File not to be a directory
        val myDir = File("no_directory_file")
        myDir.createNewFile()
        service = EmbraceCacheService(
            lazy { myDir },
            serializer,
            InternalEmbraceLogger()
        )

        val deleted = service.deleteObjectsByRegex(".*object.*")
        assertFalse(deleted)
        myDir.delete()
    }

    @Test
    fun `test moveObject with existent source`() {
        val myObject = fakeSession()
        service.cacheObject(CUSTOM_OBJECT_1_FILE_NAME, myObject, Session::class.java)

        val moved = service.moveObject(CUSTOM_OBJECT_1_FILE_NAME, CUSTOM_OBJECT_2_FILE_NAME)
        val children = checkNotNull(dir.listFiles())
        val file = children.single()

        assertTrue(moved)
        assertEquals("emb_$CUSTOM_OBJECT_2_FILE_NAME", file.name)
    }

    @Test
    fun `test moveObject with non-existent source`() {
        val moved = service.moveObject(CUSTOM_OBJECT_1_FILE_NAME, CUSTOM_OBJECT_2_FILE_NAME)
        val children = checkNotNull(dir.listFiles())

        assertFalse(moved)
        assertEquals(0, children.size)
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
        assertTrue(cachedPendingCalls.hasAnyPendingApiCall())
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
}

internal const val CUSTOM_OBJECT_1_FILE_NAME = "custom_object_1.json"
internal const val CUSTOM_OBJECT_2_FILE_NAME = "custom_object_2.json"
internal const val CUSTOM_OBJECT_3_FILE_NAME = "custom_3.json"
