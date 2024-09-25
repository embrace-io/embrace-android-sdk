package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType

data class PayloadReference(
    val fileName: String,
    val endpoint: Endpoint,
    val envelopeType: SupportedEnvelopeType,
)
