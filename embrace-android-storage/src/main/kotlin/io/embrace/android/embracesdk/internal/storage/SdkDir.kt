package io.embrace.android.embracesdk.internal.storage

/**
 * A directory the SDK may create under its storage root. Anything under the root that isn't declared here is an
 * orphan that can be deleted at any time.
 *
 * @param parent the directory this one sits in. null for root
 * @param dirName the name of this directory within its parent
 * @param allowedEntries whether an entry may exist in this directory, given its path relative to this directory as
 * a list of name segments. Only consulted for directories with no declared children.
 * @param sweepRequiresStale whether items in this directory are written concurrently, meaning entries may only be
 * removed once older than process start
 */
enum class SdkDir(
    val parent: SdkDir?,
    val dirName: String,
    val allowedEntries: (List<String>) -> Boolean = { true },
    val sweepRequiresStale: Boolean = true,
) {

    /**
     * The most recent remote config response, its etag and its binary cache
     */
    REMOTE_CONFIG(null, "remote_config", ::isRemoteConfigEntry),

    /**
     * State that persists across launches, such as ordinals and the crash marker
     */
    INSTALL_STATE(null, "install_state", ::isInstallStateEntry),

    /**
     * Holds every directory that contains telemetry
     */
    TELEMETRY(null, "telemetry", sweepRequiresStale = false),

    /**
     * Complete payloads that are ready to send
     */
    TELEMETRY_READY(TELEMETRY, "ready", ::isPayloadEntry),

    /**
     * Holds telemetry that is not yet ready to send
     */
    TELEMETRY_PENDING(TELEMETRY, "pending", sweepRequiresStale = false),

    /**
     * Incomplete payloads that are periodically snapshotted
     */
    TELEMETRY_PENDING_SNAPSHOTS(TELEMETRY_PENDING, "snapshots", ::isPayloadEntry),

    /**
     * Session telemetry grouped under one directory per session part
     */
    TELEMETRY_PENDING_SESSION_PARTS(TELEMETRY_PENDING, "session_parts", ::isSessionPartEntry),

    /**
     * Native crash reports
     */
    TELEMETRY_PENDING_NATIVE_CRASHES(TELEMETRY_PENDING, "native_crashes", ::isPayloadEntry),

    /**
     * Cached log envelopes
     */
    TELEMETRY_LOG_ENVELOPES(TELEMETRY, "log_envelopes", ::isPayloadEntry),
}
