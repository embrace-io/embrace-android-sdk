package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.sigquit.AnrThreadIdDelegate
import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl

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
