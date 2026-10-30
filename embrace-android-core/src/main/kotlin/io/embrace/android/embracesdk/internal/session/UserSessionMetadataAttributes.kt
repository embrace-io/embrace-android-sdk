package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

/**
 * The value of the [EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART] attribute when
 * the user session is background-only. The attribute is omitted otherwise.
 */
internal const val BACKGROUND_ONLY_MARKER = "1"

/**
 * The span attributes that describe this user session.
 */
internal val UserSessionMetadata.attributes: Map<String, Any>
    get() = buildMap {
        put(EmbSessionAttributes.EMB_USER_SESSION_START_TS, startTimeMs)
        put(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
        put(EmbSessionAttributes.EMB_USER_SESSION_NUMBER, userSessionNumber)
        put(EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS, maxDurationSecs)
        put(EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS, inactivityTimeoutSecs)
        if (isBackgroundOnly == true) {
            put(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART, BACKGROUND_ONLY_MARKER)
        }
    }
