package io.embrace.android.gradle.integration.framework

import io.embrace.android.gradle.config.TestMatrix
import io.embrace.android.gradle.network.FormPart
import io.embrace.android.gradle.network.MultipartFormReader
import io.embrace.android.gradle.network.validateBodyApiToken
import io.embrace.android.gradle.network.validateBodyAppId
import io.embrace.android.gradle.network.validateBodyBuildId
import io.embrace.android.gradle.network.validateMappingFile
import io.embrace.android.gradle.plugin.buildreporter.BuildTelemetryRequest
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeRequest
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File

class AssertionInterface(
    private val apiServer: FakeApiServer
) {

    private val multipartFormReader: MultipartFormReader = MultipartFormReader()

    /**
     * Fetches a request sent to the mock server with the given endpoint.
     */
    fun fetchRequest(endpoint: EmbraceEndpoint): RecordedRequest = fetchRequests(endpoint).single()

    /**
     * Fetches all requests sent to the mock server with the given endpoint.
     */
    fun fetchRequests(endpoint: EmbraceEndpoint) = apiServer.fetchRequests(endpoint)

    /**
     * Reads the parts of a multipart request.
     */
    fun readMultipartRequest(request: RecordedRequest): List<FormPart> =
        multipartFormReader.read(request)

    /**
     * Asserts that common headers (Content-Type, X-EM-AID) are present in the request.
     */
    fun assertHeaders(request: RecordedRequest, contentType: String, appId: String?) {
        assertEquals(contentType, request.getHeader("Content-Type")?.split(";")?.get(0))
        assertEquals(appId, request.getHeader("X-EM-AID"))
    }

    /**
     * Deserializes the request body into the given type.
     */
    inline fun <reified T> deserializeRequestBody(request: RecordedRequest): T {
        val json = request.body.use(Buffer::readUtf8)
        return MoshiSerializer().fromJson(json, T::class.java)
    }

    /**
     * Deserializes an expected request body from a test fixture file into the given type.
     */
    inline fun <reified T> deserializeExpectedRequestBody(
        projectDir: File,
        expectedPath: String
    ): T {
        val json = projectDir.file(expectedPath).readText()
        return MoshiSerializer().fromJson(json, T::class.java)
    }

    /**
     * Deserializes an expected request body from a test fixture file into the given type.
     */
    inline fun <reified T> compareRequestBodyAgainstExpected(
        request: RecordedRequest,
        projectDir: File,
        expectedPath: String
    ) {
        val observed = deserializeRequestBody<T>(request)
        val expected = deserializeExpectedRequestBody<T>(projectDir, expectedPath)
        assertEquals(expected, observed)
    }

    private fun AssertionInterface.verifyBuildTelemetryRequestContents(
        request: RecordedRequest,
        expectedVariants: List<String>,
        expectedAppIds: List<String>,
        testMatrix: TestMatrix
    ) {
        with(deserializeRequestBody<BuildTelemetryRequest>(request)) {
            assertNotNull(metadataRequestId)
            assertNotNull(pluginVersion)
            assertEquals(testMatrix.gradle, gradleVersion)
            assertEquals(testMatrix.agp, agpVersion)
            assertTrue(checkNotNull(isBuildCacheEnabled))
            assertFalse(checkNotNull(isConfigCacheEnabled))
            assertTrue(checkNotNull(isGradleParallelExecutionEnabled))
            assertNotNull(operatingSystem)
            assertNotNull(jreVersion)
            assertNotNull(jdkVersion)
            assertFalse(checkNotNull(isEdmEnabled))

            // assert variants match expected list and contain unique build IDs
            val variants = checkNotNull(variantMetadata)
            variants.forEachIndexed { i, variant ->
                assertNotNull(variant.buildId)
                assertTrue(expectedVariants.contains(variant.variantName))
                if (expectedAppIds.isNotEmpty()) {
                    assertEquals(expectedAppIds[i], variant.appId)
                }
            }
            assertEquals(variants.size, variants.map { it.buildId }.distinct().count())
        }
    }

    /**
     * Verifies the contents of a build telemetry request. An optional list of [expectedAppIds] can be passed in to
     * check the appIds in the requests. If not passed in, each appId would be expected to be the default test one.
     */
    fun AssertionInterface.verifyBuildTelemetryRequestSent(
        expectedVariants: List<String>,
        expectedAppIds: List<String>? = null,
        testMatrix: TestMatrix = TestMatrix.MaxVersion,
    ) {
        val request = fetchRequest(EmbraceEndpoint.BUILD_DATA)
        assertHeaders(request, "application/json", null)

        val appIds = expectedAppIds ?: defaultAppIds(expectedVariants.size)

        // assert plugin telemetry was sent
        verifyBuildTelemetryRequestContents(request, expectedVariants, appIds, testMatrix)
    }

    /**
     * Verifies expected number of JVM mapping requests were sent with the default test appId
     */
    fun AssertionInterface.verifyJvmMappingRequestsSent(expectedCount: Int) =
        verifyJvmMappingRequestsSent(appIds = defaultAppIds(expectedCount))

    /**
     * Verifies expected JVM mapping requests were sent with the specific appIds in the given order
     */
    fun AssertionInterface.verifyJvmMappingRequestsSent(appIds: List<String>, buildIds: List<String>? = null) {
        val requests = fetchRequests(EmbraceEndpoint.PROGUARD)
        assertEquals(appIds.size, requests.size)

        requests.forEachIndexed { i, request ->
            val parts = readMultipartRequest(request)
            parts[0].validateBodyAppId(appIds[i])
            parts[1].validateBodyApiToken(IntegrationTestDefaults.API_TOKEN)
            parts[2].validateBodyBuildId(buildIds?.get(i))
            parts[3].validateMappingFile("mapping.txt")
        }
    }

    /**
     * Verifies no JVM mapping requests were sent.
     */
    fun AssertionInterface.verifyNoRequestsSent(endpoint: EmbraceEndpoint) {
        val requests = fetchRequests(endpoint)
        assertEquals(0, requests.size)
    }

    fun AssertionInterface.verifyNoHandshakes() {
        val handshakes = fetchRequests(EmbraceEndpoint.NDK_HANDSHAKE).map {
            deserializeRequestBody<NdkUploadHandshakeRequest>(it)
        }
        assertEquals(0, handshakes.size)
    }

    fun AssertionInterface.verifyHandshakes(
        expectedLibs: List<String>,
        expectedArchs: List<String>,
        expectedVariants: List<String>
    ) {
        val handshakes = fetchRequests(EmbraceEndpoint.NDK_HANDSHAKE).map {
            deserializeRequestBody<NdkUploadHandshakeRequest>(it)
        }

        expectedVariants.forEach { variant ->
            val handshake = handshakes.find { it.variant == variant }
            verifyNdkHandshake(
                handshake ?: error("Handshake not found for variant: $variant"),
                variant,
                expectedLibs,
                expectedArchs
            )
        }
    }

    private fun verifyNdkHandshake(
        handshake: NdkUploadHandshakeRequest,
        expectedVariantName: String,
        expectedLibs: List<String>,
        expectedArchs: List<String>
    ) {
        assertEquals(expectedVariantName, handshake.variant)
        assertEquals(IntegrationTestDefaults.APP_ID, handshake.appId)
        assertEquals(IntegrationTestDefaults.API_TOKEN, handshake.apiToken)

        val symbols = handshake.archSymbols
        assertEquals(expectedArchs.sorted(), symbols.keys.toList().sorted())

        symbols.forEach { entry ->
            assertEquals(expectedLibs.sorted(), entry.value.keys.toList().sorted())

            expectedLibs.forEach { lib ->
                assertNotNull(entry.value[lib])
            }
        }
    }

    fun AssertionInterface.verifyNoUploads() {
        val uploads = fetchRequests(EmbraceEndpoint.NDK).map(::readMultipartRequest)
        assertEquals(0, uploads.size)
    }

    fun AssertionInterface.verifyUploads(
        expectedLibs: List<String>,
        expectedArchs: List<String>,
        expectedVariants: List<String>
    ) {
        val uploads = fetchRequests(EmbraceEndpoint.NDK).map(::readMultipartRequest)
        val expectedLibsSize = expectedLibs.size
        val expectedArchsSize = expectedArchs.size
        val expectedVariantsSize = expectedVariants.size
        // Verify that there are expectedLibs * expectedArchs * expectedVariants uploads
        assertEquals(expectedLibsSize * expectedArchsSize * expectedVariantsSize, uploads.size)

        // Validate library uploads (one for every combination of architecture and variant)
        expectedLibs.forEach {
            assertUploadsCount(
                uploads,
                fieldIndex = 6,
                expectedData = it,
                count = expectedArchsSize * expectedVariantsSize
            )
            validateUploadedFile(uploads, it)
        }

        // Validate variants (one for every combination of architecture and library)
        expectedVariants.forEach {
            assertUploadsCount(
                uploads,
                fieldIndex = 3,
                expectedData = it,
                count = expectedLibsSize * expectedArchsSize
            )
        }

        // Validate architectures (one for every combination of library and variant)
        expectedArchs.forEach { arch ->
            assertUploadsCount(
                uploads,
                fieldIndex = 4,
                expectedData = arch,
                count = expectedLibsSize * expectedVariantsSize
            )
        }

        // Validate each upload has correct appId and apiToken
        uploads.forEach { upload -> verifyNdkSymbolUpload(upload) }
    }

    /**
     * Filters the list of uploads given a specific field index and the expected data for that index.
     * Asserts that the filtered list has the expected number of elements.
     */
    private fun assertUploadsCount(
        uploads: List<List<FormPart>>,
        fieldIndex: Int,
        expectedData: String,
        count: Int
    ) {
        val filteredUploads = uploads.filter { it[fieldIndex].data == expectedData }
        assertEquals(
            "Expected $count uploads with $expectedData in field $fieldIndex",
            count,
            filteredUploads.size
        )
    }

    /**
     * Verifies that the uploads are sending a non-empty file, and the content disposition contains the expected library name.
     */
    private fun validateUploadedFile(uploads: List<List<FormPart>>, expectedLib: String) {
        val filteredUploads = uploads.filter {
            it[5].data == expectedLib
        }
        filteredUploads.forEach {
            assertFalse(it[6].data.isNullOrEmpty())
            assertTrue(it[6].contentDisposition.contains(expectedLib))
        }
    }

    private fun verifyNdkSymbolUpload(parts: List<FormPart>) {
        parts[0].validateBodyAppId(IntegrationTestDefaults.APP_ID)
        parts[1].validateBodyApiToken(IntegrationTestDefaults.API_TOKEN)
    }

    private fun defaultAppIds(size: Int) = MutableList(size) {
        IntegrationTestDefaults.APP_ID
    }
}
