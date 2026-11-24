package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

/**
 * The JSON keys of the typed [EnvelopeResource] fields. An [EnvelopeResource.extras] entry under one of
 * these keys replaces that field's value when the resource is serialized.
 */
val envelopeResourceFieldKeys: Set<String> get() = EnvelopeResourceSerializer.KNOWN_KEYS
