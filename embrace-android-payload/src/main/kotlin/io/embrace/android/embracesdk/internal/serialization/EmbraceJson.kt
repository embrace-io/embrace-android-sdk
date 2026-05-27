package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.json.Json

/**
 * Shared [Json] configuration that reproduces the wire format previously produced by Moshi:
 *
 * - [Json.encodeDefaults] = true: Moshi always writes non-null values, even when equal to the
 *   Kotlin default.
 * - [Json.explicitNulls] = false: Moshi omits null-valued properties on write; an absent key
 *   decodes to the property default.
 * - [Json.ignoreUnknownKeys] = true: equivalent to Moshi's `failOnUnknown = false`.
 *
 * `encodeDefaults` and `explicitNulls` together reproduce Moshi's "write everything non-null,
 * omit nulls" behavior. Changing either alters the wire format. Compact output and strict enum
 * decoding (`coerceInputValues = false`) are left at their defaults, which also match Moshi.
 */
internal val embraceJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
