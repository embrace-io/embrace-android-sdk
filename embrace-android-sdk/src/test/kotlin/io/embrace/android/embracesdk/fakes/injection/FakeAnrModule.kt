package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.testframework.NoopAnrService

class FakeAnrModule(
    override val anrService: AnrService = NoopAnrService,
    override val blockedThreadDetector: BlockedThreadDetector = BlockedThreadDetector(
        FakeClock(),
        state = ThreadMonitoringState(FakeClock()),
        targetThread = Thread.currentThread(),
        blockedDurationThreshold = FakeConfigService().anrBehavior.getMinDuration(),
        samplingIntervalMs = FakeConfigService().anrBehavior.getSamplingIntervalMs()
    ),
) : AnrModule
