package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopUnityInternalInterface
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

internal class NoopUnityInternalInterfaceTest {

    private lateinit var impl: NoopUnityInternalInterface

    @Before
    fun setUp() {
        impl = NoopUnityInternalInterface(
            NoopEmbraceInternalInterface(
                mockk(relaxed = true)
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
