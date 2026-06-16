package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeResourceSerializerTest {

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
        extras = mapOf("foo" to "bar"),
    )

    @Test
    fun `serializes all known fields in declaration order with extras flattened`() {
        val expected =
            """{"app_version":"1.0.0","app_framework":1,"build_id":"abc123","app_ecosystem_id":"io.embrace.test",""" +
                """"build_type":"release","build_flavor":"prod","environment":"prod","bundle_version":"100",""" +
                """"sdk_version":"6.0.0","sdk_simple_version":600,"react_native_bundle_id":"rn-bundle-123",""" +
                """"react_native_version":"0.72.0","javascript_patch_number":"1","hosted_platform_version":"2023.1",""" +
                """"hosted_sdk_version":"1.0.0","unity_build_id":"unity-123","device_manufacturer":"Google",""" +
                """"device_model":"Pixel 6","device_architecture":"arm64-v8a","jailbroken":false,""" +
                """"disk_total_capacity":64000000000,"os_type":"android","os_name":"android","os_version":"13",""" +
                """"os_code":"33","screen_resolution":"1080x2400","num_cores":8,"foo":"bar"}"""
        assertEquals(expected, embraceJson.encodeToString(EnvelopeResourceSerializer, fullResource))
    }

    @Test
    fun `serializes empty resource as empty object`() {
        assertEquals("{}", embraceJson.encodeToString(EnvelopeResourceSerializer, EnvelopeResource()))
    }

    @Test
    fun `round trips the golden full resource`() {
        val json = loadGoldenFile("envelope_resource_full.json")
        val decoded = embraceJson.decodeFromString(EnvelopeResourceSerializer, json)
        assertEquals(fullResource, decoded)
    }

    @Test
    fun `round trips the golden null resource`() {
        val json = loadGoldenFile("envelope_resource_null.json")
        val decoded = embraceJson.decodeFromString(EnvelopeResourceSerializer, json)
        assertEquals(EnvelopeResource(), decoded)
    }

    @Test
    fun `decodes unknown app framework as null`() {
        val decoded = embraceJson.decodeFromString(EnvelopeResourceSerializer, """{"app_framework":999}""")
        assertNull(decoded.appFramework)
    }

    @Test
    fun `folds unknown keys into extras`() {
        val decoded = embraceJson.decodeFromString(
            EnvelopeResourceSerializer,
            """{"app_version":"1.0.0","custom_one":"x","custom_two":"y"}""",
        )
        assertEquals("1.0.0", decoded.appVersion)
        assertEquals(mapOf("custom_one" to "x", "custom_two" to "y"), decoded.extras)
    }

    private fun loadGoldenFile(filename: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(filename)).bufferedReader().readText()
}
