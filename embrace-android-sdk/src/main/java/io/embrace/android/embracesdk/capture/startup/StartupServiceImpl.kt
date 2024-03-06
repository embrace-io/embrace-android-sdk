package io.embrace.android.embracesdk.capture.startup

import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.worker.BackgroundWorker

internal class StartupServiceImpl(
    private val spanService: SpanService,
    @Suppress("UnusedPrivateMember")
    private val backgroundWorker: BackgroundWorker
) : StartupService {

    @Volatile
    private var sdkInitStartMs: Long? = null

    @Volatile
    private var sdkInitEndMs: Long? = null

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
        sdkInitStartMs = startTimeMs
        sdkInitEndMs = endTimeMs
        sdkStartupDurationMs = endTimeMs - startTimeMs
    }

    override fun getSdkStartupDuration(coldStart: Boolean): Long? = when (coldStart) {
        true -> sdkStartupDurationMs
        false -> null
    }

    override fun getSdkInitStartMs(): Long? = sdkInitStartMs

    override fun getSdkInitEndMs(): Long? = sdkInitEndMs
}
