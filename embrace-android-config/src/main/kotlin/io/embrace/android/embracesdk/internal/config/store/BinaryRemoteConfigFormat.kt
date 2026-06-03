package io.embrace.android.embracesdk.internal.config.store

/**
 * Constants describing the on-disk binary format written by [BinaryRemoteConfigEncoder] and read by
 * [BinaryRemoteConfigDecoder].
 */
internal object BinaryRemoteConfigFormat {

    /**
     * Version of the binary layout, written as the first value of every encoded payload.
     *
     * Increment this whenever the binary layout changes in any way — a new/removed/reordered/retyped
     * field on [io.embrace.android.embracesdk.internal.config.remote.RemoteConfig] or any nested
     * config type, or a change to how a value is written. The decoder rejects any payload whose
     * version does not match, which causes the store to fall back to reading the JSON cache.
     *
     * `BinaryRemoteConfigSchemaTest` fails whenever the layout changes without this being bumped.
     */
    const val VERSION: Int = 1
}
