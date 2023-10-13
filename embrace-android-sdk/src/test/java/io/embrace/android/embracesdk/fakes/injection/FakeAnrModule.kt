package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.injection.AnrModule
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal class FakeAnrModule(
    override val anrService: AnrService = FakeAnrService(),
    override val googleAnrTimestampRepository: GoogleAnrTimestampRepository = GoogleAnrTimestampRepository(
        InternalEmbraceLogger()
    )
) : AnrModule
