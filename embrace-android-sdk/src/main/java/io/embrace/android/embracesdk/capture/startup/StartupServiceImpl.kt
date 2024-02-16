package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.SpanService

internal class StartupServiceImpl(
    private val spanService: SpanService
) : StartupService {

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    @Volatile
    private var sdkStartupDuration: Long? = null

    override fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long) {
        if (sdkStartupDuration == null) {
            spanService.recordCompletedSpan(
                name = "sdk-init",
                startTimeNanos = startTimeMs.millisToNanos(),
                endTimeNanos = endTimeMs.millisToNanos()
            )
        }
        sdkStartupDuration = endTimeMs - startTimeMs
    }

    override fun getSdkStartupInfo(coldStart: Boolean): Long? = when (coldStart) {
        true -> sdkStartupDuration
        false -> null
    }
}
