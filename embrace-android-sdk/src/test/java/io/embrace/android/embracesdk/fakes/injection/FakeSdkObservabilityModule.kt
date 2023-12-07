package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.injection.SdkObservabilityModule
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalErrorLogger
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService

internal class FakeSdkObservabilityModule(
    override val exceptionService: EmbraceInternalErrorService = EmbraceInternalErrorService(
        FakeProcessStateService(),
        FakeClock(),
        true
    )
) : SdkObservabilityModule {

    override val internalErrorLogger: InternalErrorLogger
        get() = TODO("Not yet implemented")

    override val embraceTelemetryService: EmbraceTelemetryService
        get() = TODO("Not yet implemented")
}
