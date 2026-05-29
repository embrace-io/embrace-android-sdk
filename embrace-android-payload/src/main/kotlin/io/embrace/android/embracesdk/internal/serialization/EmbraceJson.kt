package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.json.Json

/**
 * Shared [Json] configuration for Embrace internals.
 */
internal val embraceJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
