package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakePayloadFactory
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.injection.SessionModule
import io.embrace.android.embracesdk.internal.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

internal class FakeSessionModule(
    override val payloadFactory: PayloadFactory = FakePayloadFactory(),
    override val sessionPropertiesService: SessionPropertiesService = FakeSessionPropertiesService(),
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator()
) : SessionModule {

    override val payloadMessageCollatorImpl: PayloadMessageCollatorImpl
        get() = TODO("Not yet implemented")

    override val periodicSessionCacher: PeriodicSessionCacher
        get() = TODO("Not yet implemented")

    override val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
        get() = TODO("Not yet implemented")
}
