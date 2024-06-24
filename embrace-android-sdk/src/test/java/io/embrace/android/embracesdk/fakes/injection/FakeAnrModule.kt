package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.sigquit.AnrThreadIdDelegate
import io.embrace.android.embracesdk.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.injection.AnrModule
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.EmbLoggerImpl

internal class FakeAnrModule(
    override val anrService: AnrService = FakeAnrService(),
    override val anrOtelMapper: AnrOtelMapper = AnrOtelMapper(anrService, FakeClock()),
    override val sigquitDataSource: SigquitDataSource = SigquitDataSource(
        SharedObjectLoader(EmbLoggerImpl()),
        AnrThreadIdDelegate(EmbLoggerImpl()),
        fakeAnrBehavior(),
        EmbLoggerImpl(),
        FakeCurrentSessionSpan()
    )
) : AnrModule
