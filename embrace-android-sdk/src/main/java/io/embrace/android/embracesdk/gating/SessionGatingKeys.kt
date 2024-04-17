package io.embrace.android.embracesdk.gating

/**
 * Represents the session components that should be added to the payload if the feature gating is
 * enabled by config.
 * Also defines events (like crashes or error logs) that must send full session payloads.
 */
internal object SessionGatingKeys {
    const val BREADCRUMBS_TAPS = "br_tb"
    const val BREADCRUMBS_VIEWS = "br_vb"
    const val BREADCRUMBS_CUSTOM_VIEWS = "br_cv"
    const val BREADCRUMBS_WEB_VIEWS = "br_wv"
    const val BREADCRUMBS_CUSTOM = "br_cb"
    const val LOG_PROPERTIES = "log_pr"
    const val SESSION_PROPERTIES = "s_props"
    const val SESSION_ORIENTATIONS = "s_oc"
    const val SESSION_USER_TERMINATION = "s_tr"
    const val SESSION_MOMENTS = "s_mts"
    const val LOGS_INFO = "log_in"
    const val LOGS_WARN = "log_war"
    const val STARTUP_MOMENT = "mts_st"
    const val USER_PERSONAS = "ur_per"
    const val PERFORMANCE_ANR = "pr_anr"
    const val PERFORMANCE_CONNECTIVITY = "pr_ns"
    const val PERFORMANCE_NETWORK = "pr_nr"
    const val PERFORMANCE_CPU = "pr_cp"
    const val PERFORMANCE_CURRENT_DISK_USAGE = "pr_ds"

    // Events that can send full session payloads
    const val FULL_SESSION_CRASHES = "crashes"
    const val FULL_SESSION_ERROR_LOGS = "errors"
}
