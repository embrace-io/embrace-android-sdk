package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakePayloadFactory
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.injection.SessionModule
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.message.PayloadFactory
import io.embrace.android.embracesdk.session.message.V1PayloadMessageCollator
import io.embrace.android.embracesdk.session.message.V2PayloadMessageCollator
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService

internal class FakeSessionModule(
    override val payloadFactory: PayloadFactory = FakePayloadFactory(),
    override val sessionPropertiesService: SessionPropertiesService = FakeSessionPropertiesService(),
    override val sessionOrchestrator: SessionOrchestrator = FakeSessionOrchestrator()
) : SessionModule {

    override val v1PayloadMessageCollator: V1PayloadMessageCollator
        get() = TODO("Not yet implemented")

    override val v2PayloadMessageCollator: V2PayloadMessageCollator
        get() = TODO("Not yet implemented")

    override val periodicSessionCacher: PeriodicSessionCacher
        get() = TODO("Not yet implemented")

    override val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
        get() = TODO("Not yet implemented")

    override val dataCaptureOrchestrator: DataCaptureOrchestrator =
        DataCaptureOrchestrator(emptyList(), InternalEmbraceLogger(), FakeConfigService())
}
