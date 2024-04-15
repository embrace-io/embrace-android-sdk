package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class EnvelopeResourceTest {

    @Test
    fun testSerialization() {
        val resource = EnvelopeResource(
            appVersion = "1.0.0",
            bundleVersion = "1",
            appEcosystemId = "com.embrace.fake",
            appFramework = EnvelopeResource.AppFramework.NATIVE,
            buildId = "1234",
            buildType = "release",
            buildFlavor = "demo",
            environment = "prod",
            sdkVersion = "5.11.0",
            sdkSimpleVersion = 5,
            hostedPlatformVersion = "2019",
            hostedSdkVersion = "1.0.0",
            reactNativeBundleId = "fba09c9f",
            javascriptPatchNumber = "53",
            unityBuildId = "1234",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 4",
            deviceArchitecture = "arm64-v8a",
            jailbroken = false,
            diskTotalCapacity = 1000000000,
            osType = "linux",
            osName = "android",
            osVersion = "11",
            osCode = "30",
            screenResolution = "1080x1920",
            numCores = 8,
        )
        assertJsonMatchesGoldenFile("envelope_resource_expected.json", resource)
    }

    @Test
    fun testDeserialization() {
        val obj: EnvelopeResource = deserializeJsonFromResource("envelope_resource_expected.json")
        assertEquals("1.0.0", obj.appVersion)
        assertEquals("1", obj.bundleVersion)
        assertEquals("com.embrace.fake", obj.appEcosystemId)
        assertEquals(EnvelopeResource.AppFramework.NATIVE, obj.appFramework)
        assertEquals("1234", obj.buildId)
        assertEquals("release", obj.buildType)
        assertEquals("demo", obj.buildFlavor)
        assertEquals("prod", obj.environment)
        assertEquals("5.11.0", obj.sdkVersion)
        assertEquals(5, obj.sdkSimpleVersion)
        assertEquals("2019", obj.hostedPlatformVersion)
        assertEquals("1.0.0", obj.hostedSdkVersion)
        assertEquals("fba09c9f", obj.reactNativeBundleId)
        assertEquals("53", obj.javascriptPatchNumber)
        assertEquals("1234", obj.unityBuildId)
        assertEquals("Google", obj.deviceManufacturer)
        assertEquals("Pixel 4", obj.deviceModel)
        assertEquals("arm64-v8a", obj.deviceArchitecture)
        assertFalse(checkNotNull(obj.jailbroken))
        assertEquals(1000000000L, obj.diskTotalCapacity)
        assertEquals("linux", obj.osType)
        assertEquals("android", obj.osName)
        assertEquals("11", obj.osVersion)
        assertEquals("30", obj.osCode)
        assertEquals("1080x1920", obj.screenResolution)
        assertEquals(8, obj.numCores)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<EnvelopeResource>()
        Assert.assertNotNull(obj)
    }
}
