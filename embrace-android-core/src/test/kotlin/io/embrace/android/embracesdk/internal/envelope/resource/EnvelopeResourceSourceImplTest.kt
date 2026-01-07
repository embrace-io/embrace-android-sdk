package io.embrace.android.embracesdk.internal.envelope.resource

import android.os.Environment
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDevice
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
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
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(Environment::class)
            every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))
        }

        @After
        fun tearDown() {
            unmockkAll()
        }
    }

    @Test
    fun getEnvelopeResource() {
        val hostedSdkVersionInfo = UnitySdkVersionInfo(FakeKeyValueStore())
        hostedSdkVersionInfo.hostedSdkVersion = "1.2.0"
        hostedSdkVersionInfo.hostedPlatformVersion = "19"
        hostedSdkVersionInfo.unityBuildIdNumber = "5092abc"
        val source = EnvelopeResourceSourceImpl(
            FakeConfigService(),
            hostedSdkVersionInfo,
            AppEnvironment.Environment.PROD,
            FakeDevice(),
            "",
            53,
            { "fakeReactNativeBundleId" }
        ) {
            mapOf("resource-attr" to "foo")
        }
        val envelope = source.getEnvelopeResource()

        assertEquals("2.5.1", envelope.appVersion)
        assertEquals(AppFramework.NATIVE, envelope.appFramework)
        assertEquals("com.fake.package", envelope.appEcosystemId)
        assertEquals("fakeBuildId", envelope.buildId)
        assertEquals("fakeBuildType", envelope.buildType)
        assertEquals("fakeBuildFlavor", envelope.buildFlavor)
        assertEquals("prod", envelope.environment)
        assertEquals("99", envelope.bundleVersion)
        assertEquals(53, envelope.sdkSimpleVersion)
        assertEquals("fakeReactNativeBundleId", envelope.reactNativeBundleId)
        assertEquals("1.2.0", envelope.hostedSdkVersion)
        assertEquals("19", envelope.hostedPlatformVersion)
        assertEquals("5092abc", envelope.unityBuildId)
        assertEquals("Samsung", envelope.deviceManufacturer)
        assertEquals("Galaxy S10", envelope.deviceModel)
        assertEquals("arm64-v8a", envelope.deviceArchitecture)
        assertEquals(false, envelope.jailbroken)
        assertEquals(10000000L, envelope.diskTotalCapacity)
        assertEquals("linux", envelope.osType)
        assertEquals("android", envelope.osName)
        assertEquals("8.0.0", envelope.osVersion)
        assertEquals("26", envelope.osCode)
        assertEquals("1920x1080", envelope.screenResolution)
        assertEquals(8, envelope.numCores)
        assertEquals("foo", envelope.extras["resource-attr"])
    }
}
