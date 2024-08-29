package io.embrace.android.embracesdk.internal.gating

/**
 * Represents the session components that should be added to the payload if the feature gating is
 * enabled by config.
 * Also defines events (like crashes or error logs) that must send full session payloads.
 */
internal object SessionGatingKeys {
    const val BREADCRUMBS_TAPS: String = "br_tb"
    const val BREADCRUMBS_VIEWS: String = "br_vb"
    const val BREADCRUMBS_CUSTOM_VIEWS: String = "br_cv"
    const val BREADCRUMBS_WEB_VIEWS: String = "br_wv"
    const val BREADCRUMBS_CUSTOM: String = "br_cb"
    const val LOG_PROPERTIES: String = "log_pr"
    const val SESSION_PROPERTIES: String = "s_props"
    const val SESSION_ORIENTATIONS: String = "s_oc"
    const val SESSION_USER_TERMINATION: String = "s_tr"
    const val SESSION_MOMENTS: String = "s_mts"
    const val LOGS_INFO: String = "log_in"
    const val LOGS_WARN: String = "log_war"
    const val STARTUP_MOMENT: String = "mts_st"
    const val USER_PERSONAS: String = "ur_per"
    const val PERFORMANCE_ANR: String = "pr_anr"
    const val PERFORMANCE_CONNECTIVITY: String = "pr_ns"
    const val PERFORMANCE_NETWORK: String = "pr_nr"
    const val PERFORMANCE_CPU: String = "pr_cp"
    const val PERFORMANCE_CURRENT_DISK_USAGE: String = "pr_ds"

    // Events that can send full session payloads
    const val FULL_SESSION_CRASHES: String = "crashes"
    const val FULL_SESSION_ERROR_LOGS: String = "errors"
}
