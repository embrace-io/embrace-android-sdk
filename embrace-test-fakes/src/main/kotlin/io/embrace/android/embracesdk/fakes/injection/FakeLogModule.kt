package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogOrchestrator
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureService
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.injection.LogModule
import io.embrace.android.embracesdk.internal.logs.EmbraceLogService
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.network.logging.NetworkCaptureService

class FakeLogModule(
    override val logService: LogService = EmbraceLogService(
        FakeTelemetryDestination(),
        FakeConfigService(),
        FakeSessionPropertiesService(),
        FakePayloadStore(),
    ),
) : LogModule {

    override val networkCaptureService: NetworkCaptureService =
        FakeNetworkCaptureService()

    override val logOrchestrator: LogOrchestrator
        get() = FakeLogOrchestrator()

    override val networkCaptureDataSource: NetworkCaptureDataSource
        get() = FakeNetworkCaptureDataSource()

    override val attachmentService: AttachmentService = AttachmentService()
}
