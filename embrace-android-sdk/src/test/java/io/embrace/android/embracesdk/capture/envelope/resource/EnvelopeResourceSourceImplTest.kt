package io.embrace.android.embracesdk.capture.envelope.resource

import android.content.pm.PackageInfo
import android.os.Environment
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.fakes.FakeDevice
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

internal class EnvelopeResourceSourceImplTest {

    companion object {
        private val packageInfo = PackageInfo()
        private val fakeArchitecture = FakeDeviceArchitecture()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(Environment::class)

            initContext()

            every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))
        }

        @After
        fun tearDown() {
            unmockkAll()
        }

        @Suppress("DEPRECATION")
        private fun initContext() {
            packageInfo.packageName = "com.embrace.fake"
            packageInfo.versionName = "1.0.0"
            @Suppress("DEPRECATION")
            packageInfo.versionCode = 10
        }
    }

    @Test
    fun getEnvelopeResource() {
        val metadataService = FakeMetadataService()
        val hostedSdkVersionInfo = HostedSdkVersionInfo(
            FakePreferenceService()
        )
        hostedSdkVersionInfo.javaScriptPatchNumber = "js"
        hostedSdkVersionInfo.hostedSdkVersion = "1.2.0"
        hostedSdkVersionInfo.hostedPlatformVersion = "19"
        hostedSdkVersionInfo.unityBuildIdNumber = "5092abc"
        val source = EnvelopeResourceSourceImpl(
            hostedSdkVersionInfo,
            AppEnvironment.Environment.PROD,
            BuildInfo("100", "release", "oem"),
            packageInfo,
            Embrace.AppFramework.NATIVE,
            fakeArchitecture,
            FakeDevice(),
            metadataService
        )
        val envelope = source.getEnvelopeResource()

        assertEquals("1.0.0", envelope.appVersion)
        assertEquals(EnvelopeResource.AppFramework.NATIVE, envelope.appFramework)
        assertEquals("com.embrace.fake", envelope.appEcosystemId)
        assertEquals("100", envelope.buildId)
        assertEquals("release", envelope.buildType)
        assertEquals("oem", envelope.buildFlavor)
        assertEquals("prod", envelope.environment)
        assertEquals("10", envelope.bundleVersion)
        assertEquals("6.6.0-SNAPSHOT", envelope.sdkVersion)
        assertEquals(53, envelope.sdkSimpleVersion)
        assertEquals("fakeReactNativeBundleId", envelope.reactNativeBundleId)
        assertEquals("js", envelope.javascriptPatchNumber)
        assertEquals("1.2.0", envelope.hostedSdkVersion)
        assertEquals("19", envelope.hostedPlatformVersion)
        assertEquals("5092abc", envelope.unityBuildId)
        assertEquals("Samsung", envelope.deviceManufacturer)
        assertEquals("Galaxy S10", envelope.deviceModel)
        assertEquals("arm64-v8a", envelope.deviceArchitecture)
        assertEquals(false, envelope.jailbroken)
        assertEquals(10000000L, envelope.diskTotalCapacity)
        assertEquals("Android", envelope.osType)
        assertEquals("8.0.0", envelope.osVersion)
        assertEquals("26", envelope.osCode)
        assertEquals("1920x1080", envelope.screenResolution)
        assertEquals(8, envelope.numCores)
    }
}
