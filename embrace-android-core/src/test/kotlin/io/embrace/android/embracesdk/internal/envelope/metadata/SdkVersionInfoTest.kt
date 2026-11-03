package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SdkVersionInfoTest {

    private val store = FakeKeyValueStore()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val info = SdkVersionInfo(
        store,
        errorHandler,
        sdkVersionKey = "sdk",
        platformVersionKey = "platform",
        unityBuildIdKey = "unity",
        javaScriptPatchKey = "js",
    )

    @Test
    fun `an update stores every value under its key`() {
        info.update {
            hostedSdkVersion = "4.1.0"
            hostedPlatformVersion = "0.72"
            unityBuildIdNumber = "5092abc"
            javaScriptPatchNumber = "53"
        }
        assertEquals("4.1.0", info.hostedSdkVersion)
        assertEquals("0.72", info.hostedPlatformVersion)
        assertEquals("5092abc", info.unityBuildIdNumber)
        assertEquals("53", info.javaScriptPatchNumber)
        assertEquals(
            mapOf("sdk" to "4.1.0", "platform" to "0.72", "unity" to "5092abc", "js" to "53"),
            store.values(),
        )
    }

    @Test
    fun `an update writes only the values that changed in one commit`() {
        info.update { hostedSdkVersion = "1.2.0" }
        assertEquals(1, store.commitCount)

        info.update { hostedSdkVersion = "1.2.0" }
        assertEquals(1, store.commitCount)

        info.update {
            hostedSdkVersion = "1.3.0"
            hostedPlatformVersion = "19"
        }
        assertEquals(2, store.commitCount)
        assertEquals(mapOf("sdk" to "1.3.0", "platform" to "19"), store.values())
    }

    @Test
    fun `values without a key are not stored`() {
        val keyless = SdkVersionInfo(store, errorHandler)
        var count = 0
        keyless.addChangeListener { count++ }
        keyless.update {
            hostedSdkVersion = "1.2.0"
            hostedPlatformVersion = "19"
            unityBuildIdNumber = "5092abc"
            javaScriptPatchNumber = "53"
        }

        assertNull(keyless.hostedSdkVersion)
        assertNull(keyless.hostedPlatformVersion)
        assertNull(keyless.unityBuildIdNumber)
        assertNull(keyless.javaScriptPatchNumber)
        assertEquals(0, store.commitCount)
        assertEquals(0, count)
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
}
