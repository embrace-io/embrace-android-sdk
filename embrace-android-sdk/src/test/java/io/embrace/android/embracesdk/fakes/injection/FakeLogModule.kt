package io.embrace.android.embracesdk.fakes.injection

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogOrchestrator
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureService
import io.embrace.android.embracesdk.fakes.FakeNetworkLoggingService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.internal.injection.LogModule
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.logs.EmbraceLogService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.internal.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

internal class FakeLogModule(
    override val networkLoggingService: NetworkLoggingService = FakeNetworkLoggingService(),

    override val logService: LogService = EmbraceLogService(
        FakeLogWriter(),
        FakeConfigService(),
        FakeSessionPropertiesService(),
        BackgroundWorker(MoreExecutors.newDirectExecutorService()),
        EmbLoggerImpl(),
        FakeClock(),
        EmbraceSerializer()
    )
) : LogModule {

    override val networkCaptureService: NetworkCaptureService =
        FakeNetworkCaptureService()

    override val logOrchestrator: LogOrchestrator
        get() = FakeLogOrchestrator()

    override val networkCaptureDataSource: NetworkCaptureDataSource
        get() = FakeNetworkCaptureDataSource()
}
