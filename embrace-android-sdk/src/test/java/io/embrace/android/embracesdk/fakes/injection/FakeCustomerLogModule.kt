package io.embrace.android.embracesdk.fakes.injection

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.capture.connectivity.NoOpNetworkConnectivityService
import io.embrace.android.embracesdk.event.EmbraceLogMessageService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNetworkLoggingService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.injection.CustomerLogModule
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.worker.BackgroundWorker

internal class FakeCustomerLogModule(
    override val networkLoggingService: NetworkLoggingService = FakeNetworkLoggingService(),

    override val logMessageService: LogMessageService = EmbraceLogMessageService(
        FakeMetadataService(),
        FakeSessionIdTracker(),
        FakeDeliveryService(),
        FakeUserService(),
        FakeConfigService(),
        fakeEmbraceSessionProperties(),
        InternalEmbraceLogger(),
        FakeClock(),
        BackgroundWorker(MoreExecutors.newDirectExecutorService()),
        FakeGatingService(),
        NoOpNetworkConnectivityService()
    )
) : CustomerLogModule {

    override val networkCaptureService: NetworkCaptureService
        get() = TODO("Not yet implemented")
}
