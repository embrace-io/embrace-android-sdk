package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class UploadSharedObjectFilesTaskTest {

    private lateinit var server: MockWebServer
    private lateinit var project: Project
    private lateinit var mapFile: File
    private lateinit var task: UploadSharedObjectFilesTask

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        project = ProjectBuilder.builder().build()

        val compressedDir = project.file("compressed")
        File(compressedDir, "arm64-v8a").mkdirs()
        File(compressedDir, "arm64-v8a/libemb-crisps.so").writeText("crisps")
        mapFile = project.file("architecturesMap.json")
        mapFile.writeText("""{"symbols":{"arm64-v8a":{"libemb-crisps.so":"abc123"}}}""")

        task = project.tasks.register("uploadTask", UploadSharedObjectFilesTask::class.java) {
            it.variantData.set(
                AndroidCompactedVariantData(
                    name = "release",
                    flavorName = "",
                    buildTypeName = "release",
                    isBuildTypeDebuggable = false,
                    productFlavors = emptyList(),
                    sourceMapPath = "",
                ),
            )
            it.requestParams.set(
                RequestParams(
                    appId = "abcde",
                    apiToken = "12345123451234512345123451234512",
                    endpoint = EmbraceEndpoint.NDK,
                    baseUrl = server.url("api").toString(),
                    failBuildOnUploadErrors = true,
                ),
            )
            it.failBuildOnUploadErrors.set(true)
            it.compressedSharedObjectFilesDirectory.set(compressedDir)
            it.architecturesToHashedSharedObjectFilesMapJson.set(mapFile)
        }.get()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `an error is thrown when the map can't be deserialized`() {
        mapFile.writeText("invalid json")
        val exc = assertThrows(IllegalStateException::class.java) { task.onRun() }
        assertTrue(
            exc.message.orEmpty(),
            exc.message.orEmpty()
                .startsWith("Failed to read the architectures to hashed shared object files map: Failed to deserialize object"),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an error is thrown when a shared object is requested but it is not found`() {
        enqueueHandshake("""{"archs":{"arm64-v8a":["libemb-not-there.so"]}}""")
        assertError("Compressed file not found for requested symbol: arm64-v8a/libemb-not-there.so")
    }

    @Test
    fun `an error is thrown when an architecture is requested but it is not found`() {
        enqueueHandshake("""{"archs":{"brutalism":["libemb-crisps.so"]}}""")
        assertError("Requested architecture was not found")
    }

    @Test
    fun `an error is thrown when the handshake response can't be deserialized`() {
        enqueueHandshake("invalid json")
        val exc = assertThrows(IllegalStateException::class.java) { task.onRun() }
        assertTrue(
            exc.message.orEmpty(),
            exc.message.orEmpty().startsWith("Embrace HTTP request errored: ${EmbraceEndpoint.NDK_HANDSHAKE.url}"),
        )
        assertOnlyHandshakeSent()
    }

    @Test
    fun `an error is thrown when the backend sends an arch with no symbols`() {
        enqueueHandshake("""{"archs":{"arm64-v8a":[]}}""")
        assertError("An arch with no symbols was requested")
    }

    @Test
    fun `no files are uploaded when the backend requests no symbols`() {
        enqueueHandshake("""{"archs":{}}""")
        task.onRun()
        assertOnlyHandshakeSent()
    }

    @Test
    fun `no files are uploaded when the backend returns an error and failBuildOnUploadErrors is disabled`() {
        server.enqueue(MockResponse().setResponseCode(400))
        task.failBuildOnUploadErrors.set(false)
        task.onRun()
        assertOnlyHandshakeSent()
    }

    private fun enqueueHandshake(body: String) {
        server.enqueue(MockResponse().setBody(body))
    }

    private fun assertError(expectedMessage: String) {
        val exc = assertThrows(IllegalStateException::class.java) { task.onRun() }
        assertEquals(expectedMessage, exc.message)
        assertOnlyHandshakeSent()
    }

    private fun assertOnlyHandshakeSent() {
        assertEquals(1, server.requestCount)
        val request = checkNotNull(server.takeRequest(0, TimeUnit.SECONDS))
        assertEquals("/api${EmbraceEndpoint.NDK_HANDSHAKE.url}", request.path)
    }
}
