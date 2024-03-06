package io.embrace.android.embracesdk.capture.envelope.resource

import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EnvelopeResourceSourceImplTest {

    @Test
    fun getEnvelopeResource() {
        val metadataService = FakeMetadataService()
        val source = EnvelopeResourceSourceImpl(
            metadataService.getDeviceInfo(),
            metadataService.getAppInfo(),
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
        assertEquals("5ac7fe", envelope.bundleVersion)
        assertEquals("5.11.0", envelope.sdkVersion)
        assertEquals(53, envelope.sdkSimpleVersion)
        assertEquals("fakeReactNativeBundleId", envelope.reactNativeBundleId)
        assertEquals("js", envelope.javascriptPatchNumber)
        assertEquals("1.2.0", envelope.hostedSdkVersion)
        assertEquals("19", envelope.hostedPlatformVersion)
        assertEquals("5092abc", envelope.unityBuildId)
        assertEquals("Samsung", envelope.deviceManufacturer)
        assertEquals("SM-G950U", envelope.deviceModel)
        assertEquals("arm64-v8a", envelope.deviceArchitecture)
        assertEquals(false, envelope.jailbroken)
        assertEquals(10000000L, envelope.diskTotalCapacity)
        assertEquals("Android", envelope.osType)
        assertEquals("8.0.0", envelope.osVersion)
        assertEquals("26", envelope.osCode)
        assertEquals("1080x720", envelope.screenResolution)
        assertEquals(8, envelope.numCores)
    }
}
