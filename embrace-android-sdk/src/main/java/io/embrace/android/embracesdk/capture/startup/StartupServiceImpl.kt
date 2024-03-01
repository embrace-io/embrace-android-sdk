package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.internal.spans.SpanService

internal class StartupServiceImpl(
    private val spanService: SpanService
) : StartupService {

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    @Volatile
    private var sdkStartupDurationMs: Long? = null

    override fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long) {
        if (sdkStartupDurationMs == null) {
            spanService.recordCompletedSpan(
                name = "sdk-init",
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
        }
        sdkStartupDurationMs = endTimeMs - startTimeMs
    }

    override fun getSdkStartupInfo(coldStart: Boolean): Long? = when (coldStart) {
        true -> sdkStartupDurationMs
        false -> null
    }
}
