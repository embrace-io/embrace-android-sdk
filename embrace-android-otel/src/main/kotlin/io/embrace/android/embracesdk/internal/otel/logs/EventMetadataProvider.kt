package io.embrace.android.embracesdk.internal.otel.logs

/**
 * Supplies snapshots of the current SDK metadata that is attached to logs recorded with
 * addCurrentMetadata = true. The provider bundles one method per privacy scope so that all scopes
 * are always registered atomically. Further scopes (e.g. private-only attributes) can be added as
 * methods with default implementations without breaking existing implementations.
 */
fun interface EventMetadataProvider {

    /**
     * Attributes attached to every log. On a key collision with a scoped method, these win.
     */
    fun allTelemetryAttributes(): Map<String, String>

    /**
     * Attributes attached only to logs that are not marked private.
     */
    fun nonPrivateAttributes(): Map<String, String> = emptyMap()
}
