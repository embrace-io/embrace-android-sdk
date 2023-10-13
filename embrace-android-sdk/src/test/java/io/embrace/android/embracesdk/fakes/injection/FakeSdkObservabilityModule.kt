package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.injection.SdkObservabilityModule
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorLogger

internal class FakeSdkObservabilityModule(
    override val exceptionService: EmbraceInternalErrorService = EmbraceInternalErrorService(
        FakeActivityService(),
        FakeClock(),
        true
    )
) : SdkObservabilityModule {

    override val internalErrorLogger: InternalErrorLogger
        get() = TODO("Not yet implemented")
}
