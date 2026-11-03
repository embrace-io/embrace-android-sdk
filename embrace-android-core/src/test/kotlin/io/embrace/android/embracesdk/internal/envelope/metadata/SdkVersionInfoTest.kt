package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SdkVersionInfoTest {

    private val store = FakePersistedSdkStateStore()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val info = SdkVersionInfo(store, errorHandler, AppFramework.UNITY)

    @Test
    fun `unity stores every value except the javascript patch`() {
        info.updateAll()
        assertEquals("4.1.0", store.state.hostedSdkVersion)
        assertEquals("0.72", store.state.hostedPlatformVersion)
        assertEquals("5092abc", store.state.unityBuildIdNumber)
        assertNull(store.state.javaScriptPatchNumber)
    }

    @Test
    fun `react native stores every value except the unity build id`() {
        SdkVersionInfo(store, errorHandler, AppFramework.REACT_NATIVE).updateAll()
        assertEquals("4.1.0", store.state.hostedSdkVersion)
        assertEquals("0.72", store.state.hostedPlatformVersion)
        assertNull(store.state.unityBuildIdNumber)
        assertEquals("53", store.state.javaScriptPatchNumber)
    }

    @Test
    fun `flutter stores only the sdk and platform versions`() {
        SdkVersionInfo(store, errorHandler, AppFramework.FLUTTER).updateAll()
        assertEquals("4.1.0", store.state.hostedSdkVersion)
        assertEquals("0.72", store.state.hostedPlatformVersion)
        assertNull(store.state.unityBuildIdNumber)
        assertNull(store.state.javaScriptPatchNumber)
    }

    @Test
    fun `native stores nothing and notifies no one`() {
        val native = SdkVersionInfo(store, errorHandler, AppFramework.NATIVE)
        var count = 0
        native.addChangeListener { count++ }
        native.updateAll()
        assertNull(native.hostedSdkVersion)
        assertNull(native.hostedPlatformVersion)
        assertEquals(0, store.updateCount)
        assertEquals(0, count)
    }

    @Test
    fun `an update that changes values is one write and an unchanged one is none`() {
        info.update { hostedSdkVersion = "1.2.0" }
        assertEquals(1, store.updateCount)

        info.update { hostedSdkVersion = "1.2.0" }
        assertEquals(1, store.updateCount)

        info.update {
            hostedSdkVersion = "1.3.0"
            hostedPlatformVersion = "19"
        }
        assertEquals(2, store.updateCount)
        assertEquals("1.3.0", info.hostedSdkVersion)
        assertEquals("19", info.hostedPlatformVersion)
    }

    @Test
    fun `an update that clears a value keeps the others`() {
        info.update {
            hostedSdkVersion = "1.2.0"
            hostedPlatformVersion = "19"
        }
        info.update { hostedSdkVersion = null }
        assertNull(info.hostedSdkVersion)
        assertEquals("19", info.hostedPlatformVersion)
    }

    @Test
    fun `each update that changes a value notifies listeners once`() {
        var count = 0
        info.addChangeListener { count++ }

        info.update {
            hostedPlatformVersion = "19"
            hostedSdkVersion = "1.2.0"
            unityBuildIdNumber = "5092abc"
        }
        assertEquals(1, count)

        info.update { hostedPlatformVersion = "20" }
        assertEquals(2, count)

        info.update { hostedPlatformVersion = "20" }
        assertEquals(2, count)
    }

    @Test
    fun `a throwing listener does not prevent the others from being notified`() {
        var count = 0
        info.addChangeListener { error("listener failed") }
        info.addChangeListener { count++ }
        info.update { hostedSdkVersion = "1.2.0" }

        assertEquals(1, count)
        assertEquals(SdkErrorType.SessionPartListenerFail.toString(), errorHandler.sdkErrorMessages.single().msg)
    }

    private fun SdkVersionInfo.updateAll() = update {
        hostedSdkVersion = "4.1.0"
        hostedPlatformVersion = "0.72"
        unityBuildIdNumber = "5092abc"
        javaScriptPatchNumber = "53"
    }
}
