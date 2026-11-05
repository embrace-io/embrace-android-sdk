package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import okio.ByteString

/**
 * Converts this message to state, using [deviceId] and [installTimestampMs] in place of the stored values.
 */
fun PersistedSdkStateProto.toPersistedSdkState(deviceId: String, installTimestampMs: Long): PersistedSdkState =
    PersistedSdkState(
        deviceId = deviceId,
        installTimestampMs = installTimestampMs,
        userId = user_id,
        userEmail = user_email,
        userName = user_name,
        userPersonas = user_personas?.values?.toSet(),
        sessionProperties = session_properties,
        userSession = user_session?.toUserSession(),
        hostedSdkVersion = hosted_sdk_version,
        hostedPlatformVersion = hosted_platform_version,
        unityBuildIdNumber = unity_build_id_number,
        javaScriptPatchNumber = java_script_patch_number,
        rnBundleUrl = rn_bundle_url,
        rnBundleId = rn_bundle_id,
    )

/**
 * Converts this state to a message. Fields that [base] holds but this SDK doesn't know about are kept.
 */
fun PersistedSdkState.toProto(base: PersistedSdkStateProto): PersistedSdkStateProto = base.copy(
    device_id = deviceId,
    install_timestamp_ms = installTimestampMs,
    user_id = userId,
    user_email = userEmail,
    user_name = userName,
    user_personas = userPersonas?.let {
        Personas(values = it.toList(), unknownFields = base.user_personas?.unknownFields ?: ByteString.EMPTY)
    },
    session_properties = sessionProperties,
    user_session = userSession?.toProto(base.user_session),
    hosted_sdk_version = hostedSdkVersion,
    hosted_platform_version = hostedPlatformVersion,
    unity_build_id_number = unityBuildIdNumber,
    java_script_patch_number = javaScriptPatchNumber,
    rn_bundle_url = rnBundleUrl,
    rn_bundle_id = rnBundleId,
)

private fun UserSessionProto.toUserSession(): UserSessionMetadata.Classified = UserSessionMetadata.Classified(
    startTimeMs = start_time_ms,
    userSessionId = user_session_id,
    userSessionNumber = user_session_number,
    maxDurationSecs = max_duration_secs,
    inactivityTimeoutSecs = inactivity_timeout_secs,
    partIndex = part_index,
    lastActivityMs = last_activity_ms,
    isBackgroundOnly = is_background_only,
)

/**
 * Unknown fields in [base] describe a specific user session, so they are only kept while that session continues.
 */
private fun UserSessionMetadata.Classified.toProto(base: UserSessionProto?): UserSessionProto = UserSessionProto(
    start_time_ms = startTimeMs,
    user_session_id = userSessionId,
    user_session_number = userSessionNumber,
    max_duration_secs = maxDurationSecs,
    inactivity_timeout_secs = inactivityTimeoutSecs,
    part_index = partIndex,
    last_activity_ms = lastActivityMs,
    is_background_only = isBackgroundOnly,
    unknownFields = base?.takeIf { it.user_session_id == userSessionId }?.unknownFields ?: ByteString.EMPTY,
)
