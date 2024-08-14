package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSigquitDataSource
import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.injection.AnrModule

public class FakeAnrModule(
    override val anrService: AnrService = FakeAnrService(),
    override val anrOtelMapper: AnrOtelMapper = AnrOtelMapper(anrService, FakeClock()),
    override val sigquitDataSource: SigquitDataSource = FakeSigquitDataSource(),
    override val blockedThreadDetector: BlockedThreadDetector = BlockedThreadDetector(
        FakeConfigService(),
        FakeClock(),
        null,
        ThreadMonitoringState(FakeClock()),
        Thread.currentThread(),
        FakeEmbLogger()
    )
) : AnrModule
