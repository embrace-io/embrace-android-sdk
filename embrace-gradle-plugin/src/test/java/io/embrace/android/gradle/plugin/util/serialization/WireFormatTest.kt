package io.embrace.android.gradle.plugin.util.serialization

import io.embrace.android.gradle.ResourceReader
import io.embrace.android.gradle.plugin.buildreporter.BuildTelemetryRequest
import io.embrace.android.gradle.plugin.buildreporter.VariantBuildTelemetry
import io.embrace.android.gradle.plugin.tasks.buildinfo.BuildInfoExport
import io.embrace.android.gradle.plugin.tasks.ndk.ArchitecturesToHashedSharedObjectFilesMap
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeRequest
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Pins the JSON the plugin writes to the Embrace backend and to task output files. The backend relies on this
 * exact shape, so it must survive any change of serialization library.
 */
class WireFormatTest {

    private val serializer = MoshiSerializer()

    @Test
    fun `build telemetry request with all fields`() {
        val request = BuildTelemetryRequest(
            buildTelemetryId = "id",
            variantBuildTelemetry = listOf(
                VariantBuildTelemetry(variantName = "release", appId = "abcde", buildId = "build"),
                VariantBuildTelemetry(variantName = "debug"),
            ),
            embracePluginVersion = "8.0.0",
            gradleVersion = "9.0",
            agpVersion = "9.4.1",
            isBuildCacheEnabled = true,
            isConfigCacheEnabled = false,
            isGradleParallelExecutionEnabled = true,
            isIsolatedProjectsEnabled = false,
            jvmArgs = "-Xmx2g",
            operatingSystem = "Mac",
            jdkVersion = "17",
            isEdmEnabled = false,
            edmVersion = "1.2",
            kotlinVersion = "2.4.20",
            kotlinJvmTarget = "11",
            sourceCompatibility = "11",
            minSdk = 21,
            compileSdk = 36,
        )
        val expected = readExpectedJson("wire_format_build_telemetry_request_all_fields.json")
        assertEquals(expected, encode(request, BuildTelemetryRequest::class.java))
    }

    @Test
    fun `build telemetry request omits null fields`() {
        assertEquals(
            readExpectedJson("wire_format_build_telemetry_request_no_optional_fields.json"),
            encode(BuildTelemetryRequest(buildTelemetryId = "id"), BuildTelemetryRequest::class.java),
        )
    }

    @Test
    fun `ndk handshake request omits null variant`() {
        val request = NdkUploadHandshakeRequest(
            appId = "app",
            apiToken = "token",
            variant = null,
            archSymbols = mapOf("arm64-v8a" to mapOf("libfoo.so" to "hash")),
        )
        assertEquals(
            readExpectedJson("wire_format_ndk_handshake_request.json"),
            encode(request, NdkUploadHandshakeRequest::class.java),
        )
    }

    @Test
    fun `ndk handshake response ignores unknown keys`() {
        val response = serializer.fromJson(
            """{"archs":{"x86":["libfoo.so"]},"added_by_backend":1}""",
            NdkUploadHandshakeResponse::class.java,
        )
        assertEquals(mapOf("x86" to listOf("libfoo.so")), response.symbols)
    }

    @Test
    fun `ndk handshake response accepts null and missing archs`() {
        assertNull(serializer.fromJson("""{"archs":null}""", NdkUploadHandshakeResponse::class.java).symbols)
        assertNull(serializer.fromJson("{}", NdkUploadHandshakeResponse::class.java).symbols)
    }

    @Test
    fun `build info export`() {
        val export = BuildInfoExport(buildId = "build", appId = "abcde", variantName = "release")
        val expected = readExpectedJson("wire_format_build_info_export.json")
        assertEquals(expected, encode(export, BuildInfoExport::class.java))
        assertEquals(export, serializer.fromJson(expected, BuildInfoExport::class.java))
    }

    @Test
    fun `hashed shared object files map`() {
        val map = ArchitecturesToHashedSharedObjectFilesMap(
            symbols = mapOf("arm64-v8a" to mapOf("libfoo.so" to "hash1", "libbar.so" to "hash2")),
        )
        val expected = readExpectedJson("wire_format_hashed_shared_object_files_map.json")
        assertEquals(expected, encode(map, ArchitecturesToHashedSharedObjectFilesMap::class.java))
        assertEquals(map, serializer.fromJson(expected, ArchitecturesToHashedSharedObjectFilesMap::class.java))
    }

    private fun <T> encode(value: T, clazz: Class<T>): String {
        val stream = ByteArrayOutputStream()
        serializer.toJson(value, clazz, stream)
        return stream.toString(Charsets.UTF_8.name())
    }

    private fun readExpectedJson(name: String): String =
        ResourceReader.readResourceAsText(name).replace(Regex("\\s"), "")
}
