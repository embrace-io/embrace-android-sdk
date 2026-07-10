package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import kotlinx.serialization.Serializable

/**
 * Mutable session-part metadata, rewritten whenever the user info, timezone or locale changes.
 */
@Serializable
data class SessionMetadata(
    val metadata: EnvelopeMetadata,
)
