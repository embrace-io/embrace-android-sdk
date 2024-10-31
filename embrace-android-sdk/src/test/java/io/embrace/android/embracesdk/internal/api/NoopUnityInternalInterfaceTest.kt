package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopUnityInternalInterface
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import org.junit.Before
import org.junit.Test

internal class NoopUnityInternalInterfaceTest {

    private lateinit var impl: NoopUnityInternalInterface
    private lateinit var initModule: FakeInitModule
    private lateinit var openTelemetryModule: OpenTelemetryModule

    @Before
    fun setUp() {
        initModule = FakeInitModule()
        openTelemetryModule = initModule.openTelemetryModule
        impl = NoopUnityInternalInterface(
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
        impl.setUnityMetaData("unityVersion", "buildGuid", "unitySdkVersion")
        impl.logUnhandledUnityException("name", "message", "stacktrace")
        impl.logHandledUnityException("name", "message", "stacktrace")
        impl.recordIncompleteNetworkRequest(
            "https://google.com",
            "get",
            15092342340,
            15092342799,
            "errorType",
            "errorMessage",
            "traceId"
        )
        impl.recordCompletedNetworkRequest(
            "https://google.com",
            "get",
            15092342340,
            15092342799,
            140,
            2509,
            200,
            "traceId"
        )
        impl.installUnityThreadSampler()
    }
}
