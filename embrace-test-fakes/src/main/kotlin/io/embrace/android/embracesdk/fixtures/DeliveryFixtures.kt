package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.storage.PayloadReference

val fakeSessionPayloadReference = PayloadReference(
    fileName = "session.json",
    endpoint = Endpoint.SESSIONS_V2,
    envelopeType = SupportedEnvelopeType.SESSION
)

val fakeLogPayloadReference = PayloadReference(
    fileName = "log.json",
    endpoint = Endpoint.LOGS,
    envelopeType = SupportedEnvelopeType.LOG
)
