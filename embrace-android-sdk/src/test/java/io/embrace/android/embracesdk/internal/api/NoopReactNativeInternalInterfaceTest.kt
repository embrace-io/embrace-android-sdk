package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

internal class NoopReactNativeInternalInterfaceTest {

    private lateinit var impl: NoopReactNativeInternalInterface
    private lateinit var initModule: FakeInitModule
    private lateinit var openTelemetryModule: OpenTelemetryModule

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        openTelemetryModule = initModule.openTelemetryModule
        impl = NoopReactNativeInternalInterface(
            NoopEmbraceInternalInterface(
                InternalTracer(
                    openTelemetryModule.spanRepository,
                    openTelemetryModule.embraceTracer
                )
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
