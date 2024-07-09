package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class AppInfoTest {

    private val info = AppInfo(
        appVersion = "1.0",
        appFramework = Embrace.AppFramework.NATIVE.value,
        buildId = "1234",
        buildType = "release",
        buildFlavor = "demo",
        environment = "prod",
        appUpdated = false,
        appUpdatedThisLaunch = false,
        bundleVersion = "5ac7fe",
        osUpdated = false,
        osUpdatedThisLaunch = false,
        sdkSimpleVersion = "5.10.0",
        sdkVersion = "5.11.0",
        reactNativeBundleId = "fba09c9f",
        javaScriptPatchNumber = "53",
        reactNativeVersion = "0.69.2",
        buildGuid = "5092abc",
        hostedPlatformVersion = "2019",
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("app_info_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj: AppInfo = deserializeJsonFromResource("app_info_expected.json")
        assertEquals("1.0", obj.appVersion)
        assertEquals("1234", obj.buildId)
        assertEquals("release", obj.buildType)
        assertEquals("demo", obj.buildFlavor)
        assertEquals("prod", obj.environment)
        assertFalse(checkNotNull(obj.appUpdated))
        assertFalse(checkNotNull(obj.appUpdatedThisLaunch))
        assertEquals("5ac7fe", obj.bundleVersion)
        assertFalse(checkNotNull(obj.osUpdated))
        assertFalse(checkNotNull(obj.osUpdatedThisLaunch))
        assertEquals("5.10.0", obj.sdkSimpleVersion)
        assertEquals("5.11.0", obj.sdkVersion)
        assertEquals("fba09c9f", obj.reactNativeBundleId)
        assertEquals("53", obj.javaScriptPatchNumber)
        assertEquals("0.69.2", obj.reactNativeVersion)
        assertEquals("5092abc", obj.buildGuid)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<AppInfo>()
        assertNotNull(obj)
    }
}
