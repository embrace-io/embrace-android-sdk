package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeSessionService
import io.embrace.android.embracesdk.injection.SessionModule
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.SessionHandler
import io.embrace.android.embracesdk.session.SessionMessageCollator
import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionModule(
    override val backgroundActivityService: BackgroundActivityService? = null,
    override val sessionService: SessionService = FakeSessionService()
) : SessionModule {
    override val sessionHandler: SessionHandler
        get() = TODO("Not yet implemented")

    override val sessionMessageCollator: SessionMessageCollator
        get() = TODO("Not yet implemented")
}
