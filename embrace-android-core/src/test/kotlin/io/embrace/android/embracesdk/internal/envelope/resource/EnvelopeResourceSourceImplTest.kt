package io.embrace.android.embracesdk.internal.envelope.resource

import android.os.Environment
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDevice
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
            { "fakeReactNativeBundleId" },
        )
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
        assertEquals(true, envelope.usesEmmcStorage)
        assertEquals("SM8450", envelope.deviceSocModel)
    }

    @Test
    fun `listener is invoked with the current resource upon registration`() {
        val source = createSource(FakeDevice())
        val observed = mutableListOf<EnvelopeResource>()

        source.addChangeListener(observed::add)

        assertEquals(1, observed.size)
        assertEquals(source.getEnvelopeResource(), observed.single())
    }

    @Test
    fun `a changed value notifies listeners`() {
        val device = FakeDevice(screenResolution = "")
        val source = createSource(device)
        val observed = mutableListOf<EnvelopeResource>()
        source.addChangeListener(observed::add)

        device.screenResolution = "1920x1080"
        source.notifyIfChanged()

        assertEquals(2, observed.size)
        assertEquals("", observed.first().screenResolution)
        assertEquals("1920x1080", observed.last().screenResolution)
    }

    @Test
    fun `an unchanged resource does not notify listeners`() {
        val source = createSource(FakeDevice())
        val observed = mutableListOf<EnvelopeResource>()
        source.addChangeListener(observed::add)

        repeat(5) { source.notifyIfChanged() }

        assertEquals(1, observed.size)
    }

    @Test
    fun `adding an extra notifies listeners only when the value changes`() {
        val source = createSource(FakeDevice())
        val observed = mutableListOf<EnvelopeResource>()
        source.addChangeListener(observed::add)

        source.add("key", "value")
        assertEquals(2, observed.size)
        assertEquals(mapOf("key" to "value"), observed.last().extras)

        // re-adding the same value leaves the resource untouched
        source.add("key", "value")
        assertEquals(2, observed.size)

        source.add("key", "other")
        assertEquals(3, observed.size)
        assertEquals(mapOf("key" to "other"), observed.last().extras)
    }

    @Test
    fun `every listener is notified even if one throws`() {
        val device = FakeDevice(screenResolution = "")
        val source = createSource(device)
        val observed = mutableListOf<EnvelopeResource>()

        source.addChangeListener { error("listener failed") }
        source.addChangeListener(observed::add)

        device.screenResolution = "1920x1080"
        source.notifyIfChanged()

        assertEquals(2, observed.size)
        assertEquals("1920x1080", observed.last().screenResolution)
    }

    @Test
    fun `no resource is built when nothing is listening`() {
        var built = false
        val device = object : Device by FakeDevice() {
            override val screenResolution: String
                get() {
                    built = true
                    return "1920x1080"
                }
        }
        val source = createSource(device)

        source.notifyIfChanged()
        assertTrue(!built)

        val observed = mutableListOf<EnvelopeResource>()
        source.addChangeListener(observed::add)
        assertTrue(built)
        assertSame(observed.single(), observed.last())
    }

    private fun createSource(device: Device) = EnvelopeResourceSourceImpl(
        FakeConfigService(),
        UnitySdkVersionInfo(FakeKeyValueStore()),
        AppEnvironment.Environment.PROD,
        device,
        "",
        53,
        { "fakeReactNativeBundleId" },
    )
}
