package io.embrace.android.embracesdk.internal.api

import android.app.Application
import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopReactNativeInternalInterface
import org.junit.Before
import org.junit.Test

internal class NoopReactNativeInternalInterfaceTest {

    private lateinit var impl: NoopReactNativeInternalInterface

    @Before
    fun setUp() {
        impl = NoopReactNativeInternalInterface(
            NoopEmbraceInternalInterface,
        )
    }

    @Test
    fun `check no errors thrown when invoked`() {
        val ctx = Application()
        impl.logUnhandledJsException("name", "message", "type", "stacktrace")
        impl.setJavaScriptPatchNumber("number")
        impl.setReactNativeSdkVersion("version")
        impl.setReactNativeVersionNumber("version")
        impl.setJavaScriptBundleUrl(ctx, "url")
    }
}
