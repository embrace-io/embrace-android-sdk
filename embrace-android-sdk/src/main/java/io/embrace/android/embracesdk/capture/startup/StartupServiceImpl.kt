package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.internal.spans.SpansService
import java.util.concurrent.TimeUnit

internal class StartupServiceImpl(
    private val spansService: SpansService
) : StartupService {

    /**
     * SDK startup time. Only set for cold start sessions.
     */
    @Volatile
    private var sdkStartupDuration: Long? = null

    override fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long) {
        if (sdkStartupDuration == null) {
            spansService.recordCompletedSpan(
                name = "sdk-init",
                startTimeNanos = TimeUnit.MILLISECONDS.toNanos(startTimeMs),
                endTimeNanos = TimeUnit.MILLISECONDS.toNanos(endTimeMs)
            )
        }
        sdkStartupDuration = endTimeMs - startTimeMs
    }

    override fun getSdkStartupInfo(coldStart: Boolean): Long? = when (coldStart) {
        true -> sdkStartupDuration
        false -> null
    }
}
