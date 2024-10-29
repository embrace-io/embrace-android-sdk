package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopFlutterInternalInterface
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import org.junit.Before
import org.junit.Test

internal class NoopFlutterInternalInterfaceTest {

    private lateinit var impl: NoopFlutterInternalInterface
    private lateinit var initModule: FakeInitModule
    private lateinit var openTelemetryModule: OpenTelemetryModule

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        openTelemetryModule = initModule.openTelemetryModule
        impl = NoopFlutterInternalInterface(
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
        impl.setEmbraceFlutterSdkVersion("version")
        impl.logHandledDartException(
            "stack",
            "name",
            "message",
            "context",
            "library"
        )
        impl.logUnhandledDartException(
            "stack",
            "name",
            "message",
            "context",
            "library"
        )
    }
}
