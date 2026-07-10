package io.embrace.android.embracesdk.internal.delivery.storage.session

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import kotlinx.serialization.Serializable

/**
 * Immutable data for a session part, written once when the part starts.
 */
@Serializable
data class Manifest(
    val resource: EnvelopeResource,
    val version: String? = null,
    val type: String? = null,
    val sharedLibSymbolMapping: Map<String, String>? = null,
)
