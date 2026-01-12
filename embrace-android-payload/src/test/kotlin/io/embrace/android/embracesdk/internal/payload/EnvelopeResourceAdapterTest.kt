package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EnvelopeResourceAdapterTest {

    private val serializer = EmbraceSerializer()

    private val fullResource = EnvelopeResource(
        appVersion = "1.0.0",
        appFramework = AppFramework.NATIVE,
        buildId = "abc123",
        appEcosystemId = "io.embrace.test",
        buildType = "release",
        buildFlavor = "prod",
        environment = "prod",
        bundleVersion = "100",
        sdkVersion = "6.0.0",
        sdkSimpleVersion = 600,
        reactNativeBundleId = "rn-bundle-123",
        reactNativeVersion = "0.72.0",
        javascriptPatchNumber = "1",
        hostedPlatformVersion = "2023.1",
        hostedSdkVersion = "1.0.0",
        unityBuildId = "unity-123",
        deviceManufacturer = "Google",
        deviceModel = "Pixel 6",
        deviceArchitecture = "arm64-v8a",
        jailbroken = false,
        diskTotalCapacity = 64000000000L,
        osType = "android",
        osName = "android",
        osVersion = "13",
        osCode = "33",
        screenResolution = "1080x2400",
        numCores = 8,
        extras = mapOf(
            "foo" to "bar"
        )
    )
    private val emptyResource = EnvelopeResource()

    @Test
    fun testFullObject() {
        val observed = serializer.toJson(fullResource, EnvelopeResource::class.java)
        val expected = loadGoldenFile("envelope_resource_full.json")
        assertObjectsMatch(expected, observed)

        // Null fields serialization
        val emptyResource = EnvelopeResource()
        val emptyJson = serializer.toJson(emptyResource, EnvelopeResource::class.java)
        assertNotNull(emptyJson)
        assertTrue(emptyJson.startsWith("{"))
        assertTrue(emptyJson.endsWith("}"))
    }

    @Test
    fun testNullObject() {
        val observed = serializer.toJson(emptyResource, EnvelopeResource::class.java)
        val expected = loadGoldenFile("envelope_resource_null.json")
        assertObjectsMatch(expected, observed)
    }

    @Test
    fun testAppFrameworkDeserialization() {
        listOf(
            1 to AppFramework.NATIVE,
            2 to AppFramework.REACT_NATIVE,
            3 to AppFramework.UNITY,
            4 to AppFramework.FLUTTER,
        ).forEach { (value, expected) ->
            val json = """{"app_framework": $value}"""
            val resource = serializer.fromJson(json, EnvelopeResource::class.java)
            assertEquals(expected, resource.appFramework)
        }

        // unknown value handled
        val unknownJson = """{"app_framework": 999}"""
        val unknownResource = serializer.fromJson(unknownJson, EnvelopeResource::class.java)
        assertNull(unknownResource.appFramework)
    }

    private fun loadGoldenFile(filename: String): String {
        return checkNotNull(
            javaClass.classLoader?.getResourceAsStream(filename)
        ).bufferedReader().readText()
    }

    private fun assertObjectsMatch(input: String, actual: String) {
        val expected = serializer.fromJson(input, EnvelopeResource::class.java)
        val observed = serializer.fromJson(actual, EnvelopeResource::class.java)

        assertEquals(expected.appVersion, observed.appVersion)
        assertEquals(expected.appFramework, observed.appFramework)
        assertEquals(expected.buildId, observed.buildId)
        assertEquals(expected.appEcosystemId, observed.appEcosystemId)
        assertEquals(expected.buildType, observed.buildType)
        assertEquals(expected.buildFlavor, observed.buildFlavor)
        assertEquals(expected.environment, observed.environment)
        assertEquals(expected.bundleVersion, observed.bundleVersion)
        assertEquals(expected.sdkVersion, observed.sdkVersion)
        assertEquals(expected.sdkSimpleVersion, observed.sdkSimpleVersion)
        assertEquals(expected.reactNativeBundleId, observed.reactNativeBundleId)
        assertEquals(expected.reactNativeVersion, observed.reactNativeVersion)
        assertEquals(expected.javascriptPatchNumber, observed.javascriptPatchNumber)
        assertEquals(expected.hostedPlatformVersion, observed.hostedPlatformVersion)
        assertEquals(expected.hostedSdkVersion, observed.hostedSdkVersion)
        assertEquals(expected.unityBuildId, observed.unityBuildId)
        assertEquals(expected.deviceManufacturer, observed.deviceManufacturer)
        assertEquals(expected.deviceModel, observed.deviceModel)
        assertEquals(expected.deviceArchitecture, observed.deviceArchitecture)
        assertEquals(expected.jailbroken, observed.jailbroken)
        assertEquals(expected.diskTotalCapacity, observed.diskTotalCapacity)
        assertEquals(expected.osType, observed.osType)
        assertEquals(expected.osName, observed.osName)
        assertEquals(expected.osVersion, observed.osVersion)
        assertEquals(expected.osCode, observed.osCode)
        assertEquals(expected.screenResolution, observed.screenResolution)
        assertEquals(expected.numCores, observed.numCores)
        assertEquals(expected.extras, observed.extras)
    }
}
