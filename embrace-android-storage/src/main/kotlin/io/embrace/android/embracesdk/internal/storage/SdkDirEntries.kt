package io.embrace.android.embracesdk.internal.storage

/*
 * The names each leaf `SdkDir` may hold. The payload, session part and remote config names restate formats owned by
 * modules this one can't see, so contract tests in embrace-android-session-persistence and embrace-android-core check
 * them against the real writers. Allowing a new name changes the layout: raise `SdkStorageLayout.LAYOUT_VERSION`.
 */

private const val PAYLOAD_V1_TOKEN = "v1.json"
private const val PAYLOAD_V1_TOKEN_COUNT = 7
private const val PAYLOAD_V2_TOKEN = "v2.json"
private const val PAYLOAD_V2_TOKEN_COUNT = 9
private const val SESSION_PART_DIR_TOKEN_COUNT = 5
private const val SESSION_PART_DIR_VERSION_TOKEN = "v1"

private val SESSION_PART_FILE_NAMES = setOf("metadata.pb", "completed_spans.pb", "span_snapshots.pb")

private val REMOTE_CONFIG_FILE_NAMES = setOf("most_recent_response", "etag", "cached_config")

private val INSTALL_STATE_FILE_NAMES = setOf(
    SdkStorageLayout.ORDINALS_FILE_NAME,
    SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
    SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME,
    SdkStorageLayout.AEI_DELIVERED_FILE_NAME,
    SdkStorageLayout.CRASH_MARKER_FILE_NAME,
)

/**
 * A payload file named by `StoredTelemetryMetadata`, in either its v1 or v2 form.
 */
internal fun isPayloadEntry(path: List<String>): Boolean {
    val tokens = path.singleOrNull()?.split("_") ?: return false
    return when (tokens.last()) {
        PAYLOAD_V1_TOKEN -> tokens.size == PAYLOAD_V1_TOKEN_COUNT
        PAYLOAD_V2_TOKEN -> tokens.size == PAYLOAD_V2_TOKEN_COUNT
        else -> false
    }
}

/**
 * A session part directory named by `SessionPartDirectory`, or one of the files written into it.
 */
internal fun isSessionPartEntry(path: List<String>): Boolean = when (path.size) {
    1 -> isSessionPartDirName(path[0])
    2 -> isSessionPartDirName(path[0]) && path[1] in SESSION_PART_FILE_NAMES
    else -> false
}

internal fun isRemoteConfigEntry(path: List<String>): Boolean = path.singleOrNull() in REMOTE_CONFIG_FILE_NAMES

internal fun isInstallStateEntry(path: List<String>): Boolean = path.singleOrNull() in INSTALL_STATE_FILE_NAMES

private fun isSessionPartDirName(name: String): Boolean {
    val tokens = name.split("_")
    return tokens.size == SESSION_PART_DIR_TOKEN_COUNT && tokens[0] == SESSION_PART_DIR_VERSION_TOKEN
}
