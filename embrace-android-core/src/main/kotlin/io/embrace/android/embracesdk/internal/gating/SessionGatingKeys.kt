package io.embrace.android.embracesdk.internal.gating

/**
 * Represents the session components that should be added to the payload if the feature gating is
 * enabled by config.
 * Also defines events (like crashes or error logs) that must send full session payloads.
 */
public object SessionGatingKeys {
    public const val BREADCRUMBS_TAPS: String = "br_tb"
    public const val BREADCRUMBS_VIEWS: String = "br_vb"
    public const val BREADCRUMBS_CUSTOM_VIEWS: String = "br_cv"
    public const val BREADCRUMBS_WEB_VIEWS: String = "br_wv"
    public const val BREADCRUMBS_CUSTOM: String = "br_cb"
    public const val LOG_PROPERTIES: String = "log_pr"
    public const val SESSION_PROPERTIES: String = "s_props"
    public const val SESSION_ORIENTATIONS: String = "s_oc"
    public const val SESSION_USER_TERMINATION: String = "s_tr"
    public const val SESSION_MOMENTS: String = "s_mts"
    public const val LOGS_INFO: String = "log_in"
    public const val LOGS_WARN: String = "log_war"
    public const val STARTUP_MOMENT: String = "mts_st"
    public const val USER_PERSONAS: String = "ur_per"
    public const val PERFORMANCE_ANR: String = "pr_anr"
    public const val PERFORMANCE_CONNECTIVITY: String = "pr_ns"
    public const val PERFORMANCE_NETWORK: String = "pr_nr"
    public const val PERFORMANCE_CPU: String = "pr_cp"
    public const val PERFORMANCE_CURRENT_DISK_USAGE: String = "pr_ds"

    // Events that can send full session payloads
    public const val FULL_SESSION_CRASHES: String = "crashes"
    public const val FULL_SESSION_ERROR_LOGS: String = "errors"
}
