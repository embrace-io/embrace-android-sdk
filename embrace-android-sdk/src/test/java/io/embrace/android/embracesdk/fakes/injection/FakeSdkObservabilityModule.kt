package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.injection.SdkObservabilityModule
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorServiceAction

internal class FakeSdkObservabilityModule(
    override val internalErrorService: InternalErrorService = EmbraceInternalErrorService(
        FakeProcessStateService(),
        FakeClock()
    )
) : SdkObservabilityModule {

    override val reportingLoggerAction: InternalErrorServiceAction
        get() = TODO("Not yet implemented")
}
