package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopReactNativeInternalInterface
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

internal class NoopReactNativeInternalInterfaceTest {

    private lateinit var impl: NoopReactNativeInternalInterface

    @Before
    fun setUp() {
        impl = NoopReactNativeInternalInterface(
            NoopEmbraceInternalInterface(
                mockk(relaxed = true)
            )
        )
    }

    @Test
    fun `check no errors thrown when invoked`() {
        impl.logUnhandledJsException("name", "message", "type", "stacktrace")
        impl.logHandledJsException("name", "message", emptyMap(), "stacktrace")
        impl.setJavaScriptPatchNumber("number")
        impl.setReactNativeSdkVersion("version")
        impl.setReactNativeVersionNumber("version")
        impl.setJavaScriptBundleUrl(mockk(relaxed = true), "url")
        impl.setCacheableJavaScriptBundleUrl(mockk(relaxed = true), "url", true)
        impl.logRnAction("name", 1L, 2L, emptyMap(), 3, "output")
        impl.logRnView("screen")
    }
}
