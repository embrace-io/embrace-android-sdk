package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakePayloadFactory
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionSpanAttrPopulator
import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModule
import io.embrace.android.embracesdk.internal.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator

internal class FakeSessionOrchestrationModule(
    override val payloadFactory: PayloadFactory = FakePayloadFactory(),
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator(),
    override val sessionSpanAttrPopulator: SessionSpanAttrPopulator = FakeSessionSpanAttrPopulator()
) : SessionOrchestrationModule {

    override val payloadMessageCollatorImpl: PayloadMessageCollatorImpl
        get() = TODO("Not yet implemented")

    override val periodicSessionCacher: PeriodicSessionCacher
        get() = TODO("Not yet implemented")

    override val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
        get() = TODO("Not yet implemented")
}
