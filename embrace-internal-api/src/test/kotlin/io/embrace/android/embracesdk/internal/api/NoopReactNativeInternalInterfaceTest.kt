package io.embrace.android.embracesdk.internal.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopInternalTracingApi
import io.embrace.android.embracesdk.internal.api.delegate.NoopReactNativeInternalInterface
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NoopReactNativeInternalInterfaceTest {

    private lateinit var impl: NoopReactNativeInternalInterface

    @Before
    fun setUp() {
        impl = NoopReactNativeInternalInterface(
            NoopEmbraceInternalInterface(
                NoopInternalTracingApi()
            )
        )
    }

    @Test
    fun `check no errors thrown when invoked`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        impl.logUnhandledJsException("name", "message", "type", "stacktrace")
        impl.setJavaScriptPatchNumber("number")
        impl.setReactNativeSdkVersion("version")
        impl.setReactNativeVersionNumber("version")
        impl.setJavaScriptBundleUrl(ctx, "url")
    }
}
