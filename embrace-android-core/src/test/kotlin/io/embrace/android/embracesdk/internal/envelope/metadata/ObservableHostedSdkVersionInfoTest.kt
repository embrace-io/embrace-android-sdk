package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ObservableHostedSdkVersionInfoTest {

    @Test
    fun `each setter notifies listeners`() {
        val observable = ObservableHostedSdkVersionInfo(UnitySdkVersionInfo(FakeKeyValueStore()))
        var count = 0
        observable.addChangeListener { count++ }

        observable.hostedSdkVersion = "1.2.0"
        assertEquals(1, count)

        observable.hostedPlatformVersion = "19"
        assertEquals(2, count)

        observable.unityBuildIdNumber = "5092abc"
        assertEquals(3, count)
    }

    @Test
    fun `a batch notifies once rather than once per write`() {
        val observable = ObservableHostedSdkVersionInfo(UnitySdkVersionInfo(FakeKeyValueStore()))
        var count = 0
        observable.addChangeListener { count++ }

        observable.batch {
            observable.hostedPlatformVersion = "19"
            observable.hostedSdkVersion = "1.2.0"
            observable.unityBuildIdNumber = "5092abc"
        }

        assertEquals(1, count)
        assertEquals("19", observable.hostedPlatformVersion)
        assertEquals("1.2.0", observable.hostedSdkVersion)
        assertEquals("5092abc", observable.unityBuildIdNumber)
    }

    @Test
    fun `getters and setters pass through to the wrapped instance`() {
        val impl = ReactNativeSdkVersionInfo(FakeKeyValueStore())
        val observable = ObservableHostedSdkVersionInfo(impl)

        observable.hostedSdkVersion = "4.1.0"
        observable.hostedPlatformVersion = "0.72"
        observable.javaScriptPatchNumber = "53"

        assertEquals("4.1.0", impl.hostedSdkVersion)
        assertEquals("0.72", impl.hostedPlatformVersion)
        assertEquals("53", impl.javaScriptPatchNumber)
    }

    @Test
    fun `a throwing listener does not prevent the others from being notified`() {
        val observable = ObservableHostedSdkVersionInfo(NativeSdkVersionInfo())
        var count = 0
        observable.addChangeListener { error("listener failed") }
        observable.addChangeListener { count++ }
        observable.hostedSdkVersion = "1.2.0"

        assertEquals(1, count)
    }
}
