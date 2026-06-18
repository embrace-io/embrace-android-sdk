package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import kotlinx.serialization.KSerializer

/**
 * Returns this type's [SupportedEnvelopeType.envelopeSerializer], failing loudly if the type
 * stores raw bytes (e.g. [SupportedEnvelopeType.ATTACHMENT]) and therefore has no JSON envelope
 * serializer. Intended for fakes and tests that only exercise JSON-envelope payloads.
 */
internal fun SupportedEnvelopeType.requireEnvelopeSerializer(): KSerializer<Envelope<*>> =
    requireNotNull(envelopeSerializer) {
        "$this has no envelope serializer (raw bytes payload)"
    }
