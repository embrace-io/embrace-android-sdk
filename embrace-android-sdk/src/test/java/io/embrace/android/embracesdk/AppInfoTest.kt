package io.embrace.android.embracesdk

import com.google.gson.Gson
import io.embrace.android.embracesdk.app.AppFramework
import io.embrace.android.embracesdk.payload.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class AppInfoTest {

    private val info = AppInfo(
        appVersion = "1.0",
        appFramework = AppFramework.NATIVE.value,
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
        val expectedInfo = ResourceReader.readResourceAsText("app_info_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(expectedInfo, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("app_info_expected.json")
        val obj = Gson().fromJson(json, AppInfo::class.java)
        assertEquals("1.0", obj.appVersion)
        assertEquals(AppFramework.NATIVE.value, obj.appFramework)
        assertEquals("1234", obj.buildId)
        assertEquals("release", obj.buildType)
        assertEquals("demo", obj.buildFlavor)
        assertEquals("prod", obj.environment)
        assertFalse(obj.appUpdated!!)
        assertFalse(obj.appUpdatedThisLaunch!!)
        assertEquals("5ac7fe", obj.bundleVersion)
        assertFalse(obj.osUpdated!!)
        assertFalse(obj.osUpdatedThisLaunch!!)
        assertEquals("5.10.0", obj.sdkSimpleVersion)
        assertEquals("5.11.0", obj.sdkVersion)
        assertEquals("fba09c9f", obj.reactNativeBundleId)
        assertEquals("53", obj.javaScriptPatchNumber)
        assertEquals("0.69.2", obj.reactNativeVersion)
        assertEquals("5092abc", obj.buildGuid)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", AppInfo::class.java)
        assertNotNull(info)
    }
}
