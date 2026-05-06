package io.embrace.android.embracesdk.internal.instrumentation.aei

internal data class ProcessStateSummary(
    val sessionPartId: String,
    val userSessionId: String?,
) {
    val valid = sessionPartId != "" && userSessionId != ""
}
