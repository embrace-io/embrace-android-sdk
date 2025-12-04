package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageListener
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.testframework.NoopAnrService
import io.mockk.every
import io.mockk.mockk

class FakeAnrModule(
    override val anrService: AnrService = NoopAnrService,
    override val blockedThreadDetector: BlockedThreadDetector = BlockedThreadDetector(
        fakeBackgroundWorker(),
        FakeClock(),
        state = ThreadMonitoringState(FakeClock()),
        looper = mockk {
            every { thread } returns Thread.currentThread()
        },
        blockedDurationThreshold = FakeConfigService().anrBehavior.getMinDuration(),
        intervalMs = FakeConfigService().anrBehavior.getSamplingIntervalMs(),
        logger = FakeEmbLogger(),
        listener = ThreadBlockageListener { _, _ -> }
    ),
) : AnrModule
