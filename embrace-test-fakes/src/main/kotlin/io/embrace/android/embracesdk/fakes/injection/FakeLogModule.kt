package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.injection.LogModule
import io.embrace.android.embracesdk.internal.logs.LogServiceImpl
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService

class FakeLogModule(
    override val logService: LogService = LogServiceImpl(
        FakeTelemetryDestination(),
        FakeConfigService(),
        FakeSessionPropertiesService(),
        TestPlatformSerializer(),
    ),
) : LogModule {

    override val logOrchestrator: LogOrchestrator
        get() = FakeLogOrchestrator()

    override val attachmentService: AttachmentService = AttachmentService()
}

private class FakeLogOrchestrator : LogOrchestrator {

    var flushCalled: Boolean = false

    override fun flush(saveOnly: Boolean) {
        flushCalled = true
    }

    override fun handleCrash(crashId: String) {
        flush(true)
    }

    override fun onLogsAdded() {
    }
}
