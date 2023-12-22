package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.injection.SdkObservabilityModule
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorLogger
import io.embrace.android.embracesdk.logging.InternalErrorService

internal class FakeSdkObservabilityModule(
    override val internalErrorService: InternalErrorService = EmbraceInternalErrorService(
        FakeProcessStateService(),
        FakeClock(),
        true
    )
) : SdkObservabilityModule {

    override val internalErrorLogger: InternalErrorLogger
        get() = TODO("Not yet implemented")
}
